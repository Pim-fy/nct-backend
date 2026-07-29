package nct.provider.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.ops.sanction.port.SanctionStatusReader;
import nct.provider.dto.ProviderProfileRequest;
import nct.provider.dto.ProviderProfileResponse;
import nct.provider.mapper.ProviderProfileMapper;

/** 담당자 7, F-PROV-004: 승인·제재 계약을 소비해 제공자 프로필을 관리한다. */
@Service
@RequiredArgsConstructor
public class ProviderProfileService {
    private final ProviderProfileMapper mapper;
    private final ProviderApplicationService providerApplicationService;
    private final SanctionStatusReader sanctionStatusReader;
    private final AuthMemberPort authMemberPort;

    @Transactional(readOnly = true)
    public ProviderProfileResponse getMine(Long userSn) {
        requireActiveProvider(userSn);
        return mapper.findActiveByUserSn(userSn).orElseGet(() -> emptyProfile(userSn));
    }

    @Transactional
    public ProviderProfileResponse updateMine(Long userSn, ProviderProfileRequest request) {
        requireActiveProvider(userSn);
        if (request == null) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        mapper.upsert(userSn, trimToNull(request.getIntroduction()), trimToNull(request.getAvailableArea()), String.valueOf(userSn));
        return mapper.findActiveByUserSn(userSn).orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Transactional(readOnly = true)
    public ProviderProfileResponse getPublic(Long providerUserSn) {
        requireActiveProvider(providerUserSn);
        return mapper.findActiveByUserSn(providerUserSn).orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }

    private void requireActiveProvider(Long userSn) {
        if (userSn == null || userSn <= 0) throw new CustomException(ErrorCode.UNAUTHORIZED);
        AuthMember member = authMemberPort.findById(userSn)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        if (!"USRC0001".equals(member.getStatus())) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }
        providerApplicationService.requireAnyActivePermission(userSn);
        sanctionStatusReader.requireNoActiveSanction(userSn);
    }

    private ProviderProfileResponse emptyProfile(Long userSn) {
        return ProviderProfileResponse.builder().userSn(userSn).reviewAverageScore(java.math.BigDecimal.ZERO).reviewCount(0L).build();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
