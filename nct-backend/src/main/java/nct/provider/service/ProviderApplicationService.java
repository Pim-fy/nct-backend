package nct.provider.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.service.ReferenceDataService;
import nct.provider.domain.ProviderApplicationCommand;
import nct.provider.dto.ProviderApplicationFileRequest;
import nct.provider.dto.ProviderApplicationRequest;
import nct.provider.dto.ProviderApplicationResponse;
import nct.provider.mapper.ProviderApplicationMapper;

/**
 * 담당자 7 · F-PROV-002/003/006/007/012~014.
 * 제공자 신청 화면과 관리자 심사 화면에서 사용하며, 승인될 때만 카테고리별 권한을 만든다.
 */
@Service @RequiredArgsConstructor
public class ProviderApplicationService {
    private static final String SERVICE_DOMAIN = "CATC0002";
    private static final String PENDING = "PRVC0002";
    private static final String APPROVED = "PRVC0003";
    private static final String REJECTED = "PRVC0004";
    private static final String NEW_APPLICATION = "PRVC0009";
    private static final Set<String> FILE_TYPES = Set.of("PRVC0012", "PRVC0013", "PRVC0014");
    private final ProviderApplicationMapper mapper;
    private final ReferenceDataService referenceDataService;
    private final FileStorageService fileStorageService;

    @Transactional
    public List<ProviderApplicationResponse> apply(Long userSn, ProviderApplicationRequest request) {
        requireUser(userSn);
        if (!mapper.isEmailCertified(userSn)) throw new CustomException(ErrorCode.EMAIL_VERIFICATION_NOT_VERIFIED);
        if (request == null || request.getCategorySns() == null) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        List<Long> categories = List.copyOf(new LinkedHashSet<>(request.getCategorySns()));
        if (categories.isEmpty() || categories.size() > 5) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        String reason = request.getReason() == null ? null : request.getReason().trim();
        if (reason != null && reason.length() > 4000) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        Map<Long, List<ProviderApplicationFileRequest>> filesByCategory = validateFiles(userSn, categories, request.getFiles());
        for (Long categorySn : categories) {
            referenceDataService.requireActiveCategory(categorySn, SERVICE_DOMAIN);
            if (filesByCategory.getOrDefault(categorySn, List.of()).isEmpty()) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            if (mapper.countActivePending(userSn, categorySn) > 0) throw new CustomException(ErrorCode.CONFLICT);
            // 이미 승인된 카테고리는 다시 신청하면 승인 단계의 활성 권한 UNIQUE 제약과 충돌한다.
            if (mapper.hasActivePermission(userSn, categorySn)) throw new CustomException(ErrorCode.CONFLICT);
            ProviderApplicationCommand command = ProviderApplicationCommand.builder().userSn(userSn).categorySn(categorySn)
                    .reason(reason).actorId(actorId(userSn)).build();
            if (mapper.insertApplication(command) != 1 || command.getApplicationSn() == null
                    || mapper.insertStatus(command.getApplicationSn(), "PRVC0016", null, actorId(userSn)) != 1) {
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            for (ProviderApplicationFileRequest file : filesByCategory.getOrDefault(categorySn, List.of())) {
                if (mapper.insertApplicationFile(command.getApplicationSn(), file.getFlSn(), file.getFileTypeCode(), actorId(userSn)) != 1) {
                    throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
                }
            }
        }
        return enrichFiles(mapper.findMine(userSn));
    }

    @Transactional(readOnly = true)
    public List<ProviderApplicationResponse> getMine(Long userSn) { requireUser(userSn); return enrichFiles(mapper.findMine(userSn)); }
    @Transactional(readOnly = true)
    public List<ProviderApplicationResponse> getForAdmin(String statusCode) { return enrichFiles(mapper.findForAdmin(normalizeStatus(statusCode))); }

    @Transactional
    public ProviderApplicationResponse approve(Long applicationSn, Long actorUserSn) {
        ProviderApplicationResponse application = requirePending(applicationSn);
        if (mapper.changeApplicationStatus(applicationSn, APPROVED, null, actorId(actorUserSn)) != 1
                || mapper.insertStatus(applicationSn, "PRVC0017", null, actorId(actorUserSn)) != 1
                || mapper.insertActivePermission(application.getUserSn(), application.getCategorySn(), applicationSn, actorId(actorUserSn)) != 1) {
            throw new CustomException(ErrorCode.CONFLICT);
        }
        return enrichFiles(mapper.findForUpdate(applicationSn).orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND)));
    }

    @Transactional
    public ProviderApplicationResponse reject(Long applicationSn, String reason, Long actorUserSn) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 4000) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        requirePending(applicationSn);
        if (mapper.changeApplicationStatus(applicationSn, REJECTED, reason.trim(), actorId(actorUserSn)) != 1
                || mapper.insertStatus(applicationSn, "PRVC0018", reason.trim(), actorId(actorUserSn)) != 1) throw new CustomException(ErrorCode.CONFLICT);
        return enrichFiles(mapper.findForUpdate(applicationSn).orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND)));
    }

    /** 다른 제공자 전용 API가 호출해 카테고리별 승인 권한을 서버에서 검증하는 재사용 계약입니다. */
    @Transactional(readOnly = true)
    public void requireCategoryPermission(Long userSn, Long categorySn) {
        requireUser(userSn); referenceDataService.requireActiveCategory(categorySn, SERVICE_DOMAIN);
        if (!mapper.hasActivePermission(userSn, categorySn)) throw new CustomException(ErrorCode.FORBIDDEN);
    }
    private ProviderApplicationResponse requirePending(Long applicationSn) {
        if (applicationSn == null || applicationSn <= 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        ProviderApplicationResponse application = mapper.findForUpdate(applicationSn).orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        if (!PENDING.equals(application.getStatusCode())) throw new CustomException(ErrorCode.ALREADY_PROCESSED);
        return application;
    }
    private String normalizeStatus(String statusCode) { if (statusCode == null || statusCode.isBlank()) return null; if (!List.of(PENDING, APPROVED, REJECTED).contains(statusCode)) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE); return statusCode; }
    private void requireUser(Long userSn) { if (userSn == null || userSn <= 0) throw new CustomException(ErrorCode.UNAUTHORIZED); }
    private String actorId(Long userSn) { requireUser(userSn); return String.valueOf(userSn); }
    private Map<Long, List<ProviderApplicationFileRequest>> validateFiles(Long userSn, List<Long> categories, List<ProviderApplicationFileRequest> files) {
        if (files == null || files.isEmpty()) return Map.of();
        Set<Long> categorySet = Set.copyOf(categories);
        Set<String> duplicates = new java.util.HashSet<>();
        Set<Long> fileSns = new java.util.HashSet<>();
        for (ProviderApplicationFileRequest file : files) {
            if (file == null || file.getCategorySn() == null || file.getFlSn() == null || file.getFileTypeCode() == null) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
            if (!categorySet.contains(file.getCategorySn()) || !FILE_TYPES.contains(file.getFileTypeCode())) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
            String key = file.getCategorySn() + ":" + file.getFileTypeCode() + ":" + file.getFlSn();
            if (!duplicates.add(key)) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            if (!fileSns.add(file.getFlSn())) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            fileStorageService.requireOwnedActiveFile(file.getFlSn(), userSn);
        }
        return files.stream().collect(Collectors.groupingBy(ProviderApplicationFileRequest::getCategorySn));
    }
    private List<ProviderApplicationResponse> enrichFiles(List<ProviderApplicationResponse> applications) {
        applications.forEach(this::enrichFiles);
        return applications;
    }
    private ProviderApplicationResponse enrichFiles(ProviderApplicationResponse application) {
        application.setFiles(mapper.findFilesByApplicationSn(application.getApplicationSn()));
        return application;
    }
}
