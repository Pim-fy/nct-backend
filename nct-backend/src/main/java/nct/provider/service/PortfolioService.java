package nct.provider.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.file.domain.FileMeta;
import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.ops.sanction.port.SanctionStatusReader;
import nct.provider.domain.PortfolioRecord;
import nct.provider.dto.PortfolioRequest;
import nct.provider.dto.PortfolioResponse;
import nct.provider.mapper.PortfolioMapper;

/** 담당자 7, F-PROV-005: 활성 제공자의 포트폴리오와 이미지 연결을 한 트랜잭션으로 관리한다. */
@Service
@RequiredArgsConstructor
public class PortfolioService {
    private static final String PORTFOLIO_PATH_PREFIX = "/api/attachment/portfolio/";

    private final PortfolioMapper mapper;
    private final ProviderApplicationService providerApplicationService;
    private final SanctionStatusReader sanctionStatusReader;
    private final FileStorageService fileStorageService;
    private final AuthMemberPort authMemberPort;

    @Transactional(readOnly = true)
    public List<PortfolioResponse> getMine(Long userSn) {
        requireActiveProvider(userSn);
        return mapper.findActiveByUserSn(userSn);
    }

    @Transactional(readOnly = true)
    public List<PortfolioResponse> getPublic(Long providerUserSn) {
        requireActiveProvider(providerUserSn);
        return mapper.findActiveByUserSn(providerUserSn);
    }

    @Transactional
    public PortfolioResponse create(Long userSn, PortfolioRequest request) {
        requireActiveProvider(userSn);
        validateRequestFiles(request, userSn);

        PortfolioRecord portfolio = PortfolioRecord.builder()
                .userSn(userSn)
                .title(request.getTitle().trim())
                .content(trimToNull(request.getContent()))
                .actorId(String.valueOf(userSn))
                .build();
        mapper.insert(portfolio);
        mapper.insertFiles(portfolio.getPortfolioSn(), request.getFileIds(), portfolio.getActorId());
        return findOwned(portfolio.getPortfolioSn(), userSn);
    }

    @Transactional
    public PortfolioResponse update(Long userSn, Long portfolioSn, PortfolioRequest request) {
        requireActiveProvider(userSn);
        findOwned(portfolioSn, userSn);
        validateRequestFiles(request, userSn);

        String actorId = String.valueOf(userSn);
        if (mapper.update(portfolioSn, userSn, request.getTitle().trim(),
                trimToNull(request.getContent()), actorId) != 1) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }
        mapper.deleteFiles(portfolioSn);
        mapper.insertFiles(portfolioSn, request.getFileIds(), actorId);
        return findOwned(portfolioSn, userSn);
    }

    @Transactional
    public void delete(Long userSn, Long portfolioSn) {
        requireActiveProvider(userSn);
        findOwned(portfolioSn, userSn);

        mapper.deleteFiles(portfolioSn);
        if (mapper.softDelete(portfolioSn, userSn, String.valueOf(userSn)) != 1) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }
    }

    private PortfolioResponse findOwned(Long portfolioSn, Long userSn) {
        if (portfolioSn == null || portfolioSn <= 0) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }
        return mapper.findActiveOwned(portfolioSn, userSn)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }

    private void validateRequestFiles(PortfolioRequest request, Long userSn) {
        if (request == null || request.getFileIds() == null || request.getFileIds().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Set<Long> uniqueFileIds = new HashSet<>();
        for (Long fileId : request.getFileIds()) {
            if (fileId == null || fileId <= 0 || !uniqueFileIds.add(fileId)) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
            FileMeta file = fileStorageService.requireOwnedActiveFile(fileId, userSn);
            if (file.getFlPath() == null || !file.getFlPath().startsWith(PORTFOLIO_PATH_PREFIX)) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }
    }

    private void requireActiveProvider(Long userSn) {
        if (userSn == null || userSn <= 0) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        AuthMember member = authMemberPort.findById(userSn)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        if (!"USRC0001".equals(member.getStatus())) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }
        providerApplicationService.requireAnyActivePermission(userSn);
        sanctionStatusReader.requireNoActiveSanction(userSn);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
