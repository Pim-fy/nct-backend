package nct.provider.service;

import java.util.HashSet;
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
import nct.member.dto.AdminMemberIdentityResponse;
import nct.member.port.AdminMemberIdentityReader;
import nct.notification.service.NotificationService;
import nct.ops.reference.service.ReferenceDataService;
import nct.provider.domain.ProviderApplicationCommand;
import nct.provider.dto.ProviderApplicationFileRequest;
import nct.provider.dto.ProviderApplicationRequest;
import nct.provider.dto.ProviderApplicationResponse;
import nct.provider.mapper.ProviderApplicationMapper;

/**
 * 담당자 6 · F-PROV-002/003/006/007/012~014.
 * 제공자 신청 화면과 관리자 심사 화면에서 사용하며, 승인될 때만 카테고리별 권한을 만든다.
 * (헤더의 "담당자 7" 표기는 업무분장 변경 전 잔재라 정정 — 2026-08-05)
 */
@Service
@RequiredArgsConstructor
public class ProviderApplicationService {
    private static final String SERVICE_DOMAIN = "CATC0002";
    private static final String PENDING = "PRVC0002";
    private static final String APPROVED = "PRVC0003";
    private static final String REJECTED = "PRVC0004";
    // 상태이력(PROVIDER_APPLY_STATUS_HIST) 기록용 코드 — 신청/승인/반려 시점마다 한 행씩 남긴다.
    // 위 신청 상태 코드는 상수인데 이력 코드만 리터럴로 흩어져 있던 것을 통일 (2026-08-05 점검 정리)
    private static final String HIST_REQUESTED = "PRVC0016";
    private static final String HIST_APPROVED = "PRVC0017";
    private static final String HIST_REJECTED = "PRVC0018";
    private static final Set<String> FILE_TYPES = Set.of("PRVC0012", "PRVC0013", "PRVC0014");
    private final ProviderApplicationMapper mapper;
    private final ReferenceDataService referenceDataService;
    private final FileStorageService fileStorageService;
    private final AdminMemberIdentityReader memberIdentityReader;
    private final NotificationService notificationService;

    @Transactional
    public List<ProviderApplicationResponse> apply(Long userSn, ProviderApplicationRequest request) {
        requireUser(userSn);
        if (!mapper.isEmailCertified(userSn)) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_NOT_VERIFIED);
        }
        if (request == null || request.getCategorySns() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        List<Long> categories = List.copyOf(new LinkedHashSet<>(request.getCategorySns()));
        if (categories.isEmpty() || categories.size() > 5) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        String reason = request.getReason() == null ? null : request.getReason().trim();
        if (reason != null && reason.length() > 4000) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Map<Long, List<ProviderApplicationFileRequest>> filesByCategory =
                validateFiles(userSn, categories, request.getFiles());

        for (Long categorySn : categories) {
            referenceDataService.requireActiveCategory(categorySn, SERVICE_DOMAIN);
            if (filesByCategory.getOrDefault(categorySn, List.of()).isEmpty()) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
            if (mapper.countActivePending(userSn, categorySn) > 0) {
                throw new CustomException(ErrorCode.CONFLICT);
            }
            // 이미 승인된 카테고리는 다시 신청하면 승인 단계의 활성 권한 UNIQUE 제약과 충돌한다.
            if (mapper.hasActivePermission(userSn, categorySn)) {
                throw new CustomException(ErrorCode.CONFLICT);
            }

            ProviderApplicationCommand command = ProviderApplicationCommand.builder()
                    .userSn(userSn)
                    .categorySn(categorySn)
                    .reason(reason)
                    .actorId(actorId(userSn))
                    .build();

            if (mapper.insertApplication(command) != 1 || command.getApplicationSn() == null
                    || mapper.insertStatus(command.getApplicationSn(), HIST_REQUESTED, null, actorId(userSn)) != 1) {
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }

            for (ProviderApplicationFileRequest file : filesByCategory.getOrDefault(categorySn, List.of())) {
                if (mapper.insertApplicationFile(
                        command.getApplicationSn(),
                        file.getFlSn(),
                        file.getFileTypeCode(),
                        actorId(userSn)) != 1) {
                    throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
                }
            }
        }
        return enrichFiles(mapper.findMine(userSn));
    }

    @Transactional(readOnly = true)
    public List<ProviderApplicationResponse> getMine(Long userSn) {
        requireUser(userSn);
        return enrichFiles(mapper.findMine(userSn));
    }

    @Transactional(readOnly = true)
    public List<ProviderApplicationResponse> getForAdmin(String statusCode) {
        return enrichAdminIdentities(enrichFiles(mapper.findForAdmin(normalizeStatus(statusCode))));
    }

    @Transactional
    public ProviderApplicationResponse approve(Long applicationSn, String reason, Long actorUserSn) {
        String normalizedReason = requireDecisionReason(reason);
        ProviderApplicationResponse application = requirePending(applicationSn);
        if (mapper.changeApplicationStatus(applicationSn, APPROVED, null, actorId(actorUserSn)) != 1
                || mapper.insertStatus(
                        applicationSn, HIST_APPROVED, normalizedReason, actorId(actorUserSn)) != 1
                || mapper.insertActivePermission(
                        application.getUserSn(),
                        application.getCategorySn(),
                        applicationSn,
                        actorId(actorUserSn)) != 1) {
            throw new CustomException(ErrorCode.CONFLICT);
        }
        notificationService.notifyProviderApprovalResult(application.getUserSn(), true, null);
        return enrichFiles(
                mapper.findForUpdate(applicationSn).orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND)));
    }

    @Transactional
    public ProviderApplicationResponse reject(Long applicationSn, String reason, Long actorUserSn) {
        String normalizedReason = requireDecisionReason(reason);
        ProviderApplicationResponse application = requirePending(applicationSn);
        if (mapper.changeApplicationStatus(
                    applicationSn, REJECTED, normalizedReason, actorId(actorUserSn)) != 1
                || mapper.insertStatus(
                    applicationSn, HIST_REJECTED, normalizedReason, actorId(actorUserSn)) != 1) {
            throw new CustomException(ErrorCode.CONFLICT);
        }
        notificationService.notifyProviderApprovalResult(application.getUserSn(), false, normalizedReason);
        return enrichFiles(
                mapper.findForUpdate(applicationSn).orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND)));
    }

    /** 다른 제공자 전용 API가 호출해 카테고리별 승인 권한을 서버에서 검증하는 재사용 계약입니다. */
    @Transactional(readOnly = true)
    public void requireCategoryPermission(Long userSn, Long categorySn) {
        requireUser(userSn);
        referenceDataService.requireActiveCategory(categorySn, SERVICE_DOMAIN);
        if (!mapper.hasActivePermission(userSn, categorySn)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    /** @ai_generated F-PROV-015: SERVICE 모드 진입에는 활성 승인 카테고리 하나 이상이 필요하다. */
    @Transactional(readOnly = true)
    public void requireAnyActivePermission(Long userSn) {
        requireUser(userSn);
        if (!mapper.hasAnyActivePermission(userSn)) throw new CustomException(ErrorCode.FORBIDDEN);
    }

    private ProviderApplicationResponse requirePending(Long applicationSn) {
        if (applicationSn == null || applicationSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        ProviderApplicationResponse application =
                mapper.findForUpdate(applicationSn).orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        if (!PENDING.equals(application.getStatusCode())) {
            throw new CustomException(ErrorCode.ALREADY_PROCESSED);
        }
        return application;
    }

    private String normalizeStatus(String statusCode) {
        if (statusCode == null || statusCode.isBlank()) {
            return null;
        }
        if (!List.of(PENDING, APPROVED, REJECTED).contains(statusCode)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return statusCode;
    }

    private String requireDecisionReason(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 4000) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return reason.trim();
    }

    private void requireUser(Long userSn) {
        if (userSn == null || userSn <= 0) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
    }

    private String actorId(Long userSn) {
        requireUser(userSn);
        return String.valueOf(userSn);
    }

    private Map<Long, List<ProviderApplicationFileRequest>> validateFiles(
            Long userSn,
            List<Long> categories,
            List<ProviderApplicationFileRequest> files) {
        if (files == null || files.isEmpty()) {
            return Map.of();
        }

        Set<Long> categorySet = Set.copyOf(categories);
        Set<String> duplicates = new HashSet<>();
        Set<Long> fileSns = new HashSet<>();

        for (ProviderApplicationFileRequest file : files) {
            if (file == null || file.getCategorySn() == null || file.getFlSn() == null || file.getFileTypeCode() == null) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
            if (!categorySet.contains(file.getCategorySn()) || !FILE_TYPES.contains(file.getFileTypeCode())) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }

            String key = file.getCategorySn() + ":" + file.getFileTypeCode() + ":" + file.getFlSn();
            if (!duplicates.add(key)) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
            if (!fileSns.add(file.getFlSn())) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
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

    /** 담당자 7 연계 · F-PROV-003: 관리자 심사 목록의 신청자와 처리자를 회원 읽기 계약으로 조립합니다. */
    private List<ProviderApplicationResponse> enrichAdminIdentities(
            List<ProviderApplicationResponse> applications) {
        Set<Long> userSns = new LinkedHashSet<>();
        for (ProviderApplicationResponse application : applications) {
            if (application.getUserSn() != null) userSns.add(application.getUserSn());
            if (application.getProcessorUserSn() != null) userSns.add(application.getProcessorUserSn());
        }
        Map<Long, AdminMemberIdentityResponse> identities = memberIdentityReader.findByUserSns(userSns);
        for (ProviderApplicationResponse application : applications) {
            application.setApplicantMember(identityOf(identities, application.getUserSn()));
            application.setProcessorMember(identityOf(identities, application.getProcessorUserSn()));
        }
        return applications;
    }

    private AdminMemberIdentityResponse identityOf(
            Map<Long, AdminMemberIdentityResponse> identities,
            Long userSn) {
        return userSn == null ? null : identities.get(userSn);
    }
}
