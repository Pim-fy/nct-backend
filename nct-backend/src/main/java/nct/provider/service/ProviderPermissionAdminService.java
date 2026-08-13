package nct.provider.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.notification.domain.NotificationAudience;
import nct.notification.domain.NotificationDomain;
import nct.notification.domain.NotificationType;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.sanction.port.SanctionStatusReader;
import nct.provider.dto.ProviderApplicationResponse;
import nct.provider.mapper.ProviderApplicationMapper;

/**
 * 담당자 7 · F-PROV-007: 승인 신청 이력은 유지하고 카테고리별 제공자 권한만 정지·복구합니다.
 * 계정 영구 정지나 ROLE 변경은 이 서비스에서 처리하지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class ProviderPermissionAdminService {

    private static final String APPROVED = "PRVC0003";
    private static final String ACTIVE = "PRVC0006";
    private static final String SUSPENDED = "PRVC0007";
    private static final String USE_Y = "Y";
    private static final String USE_N = "N";

    private final ProviderApplicationMapper mapper;
    private final AuthMemberPort authMemberPort;
    private final SanctionStatusReader sanctionStatusReader;
    private final AuditLogPort auditLogPort;
    private final NotificationService notificationService;

    @Transactional
    public ProviderApplicationResponse changePermission(
            Long applicationSn,
            boolean active,
            String reason,
            String requestId,
            Long actorUserSn) {
        String normalizedReason = validate(applicationSn, reason, requestId, actorUserSn);
        ProviderApplicationResponse application = mapper.findForUpdate(applicationSn)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        if (!APPROVED.equals(application.getStatusCode()) || application.getPermissionSn() == null) {
            throw new CustomException(ErrorCode.CONFLICT, "승인되어 발급된 제공자 권한만 변경할 수 있습니다.");
        }

        String targetStatus = active ? ACTIVE : SUSPENDED;
        String targetUseYn = active ? USE_Y : USE_N;
        if (targetStatus.equals(application.getPermissionStatusCode())
                && targetUseYn.equals(application.getPermissionUseYn())) {
            return application;
        }
        if (active && mapper.hasActivePermission(application.getUserSn(), application.getCategorySn())) {
            throw new CustomException(ErrorCode.CONFLICT, "해당 카테고리의 활성 권한이 이미 존재합니다.");
        }
        if (active) {
            AuthMember member = authMemberPort.findById(application.getUserSn())
                    .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
            if (!"USRC0001".equals(member.getStatus())) {
                throw new CustomException(ErrorCode.CONFLICT, "정상 상태의 회원만 제공자 권한을 복구할 수 있습니다.");
            }
            sanctionStatusReader.requireNoActiveSanction(application.getUserSn());
        }

        String before = summary(application);
        if (mapper.updatePermissionStatus(
                application.getPermissionSn(),
                application.getPermissionStatusCode(),
                application.getPermissionUseYn(),
                targetStatus,
                targetUseYn,
                String.valueOf(actorUserSn)) != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "제공자 권한 상태가 이미 변경되었습니다.");
        }

        ProviderApplicationResponse updated = mapper.findForUpdate(applicationSn)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        auditLogPort.record(new AuditLogCommand(
                "STATUS_CHANGE",
                String.valueOf(actorUserSn),
                RefType.PROVIDER_APPLICATION.getCode(),
                updated.getApplicationSn(),
                normalizedReason,
                before,
                summary(updated),
                requestId.trim(),
                RefType.MEMBER.getCode(),
                updated.getUserSn()));
        notificationService.notify(
                updated.getUserSn(),
                NotificationType.OPS,
                NotificationDomain.SERVICE,
                active ? NotificationAudience.PROVIDER : NotificationAudience.GENERAL,
                active ? "제공자 권한이 복구되었습니다" : "제공자 권한이 정지되었습니다",
                updated.getCategoryName() + " 제공자 권한이 " + (active ? "복구" : "정지")
                        + "되었습니다. 사유: " + normalizedReason,
                RefType.MEMBER,
                updated.getUserSn());
        return updated;
    }

    private String validate(
            Long applicationSn,
            String reason,
            String requestId,
            Long actorUserSn) {
        if (applicationSn == null || applicationSn <= 0
                || actorUserSn == null || actorUserSn <= 0
                || reason == null || reason.isBlank() || reason.trim().length() > 1000
                || requestId == null || requestId.isBlank() || requestId.trim().length() > 100) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return reason.trim();
    }

    private String summary(ProviderApplicationResponse application) {
        return "application=" + application.getApplicationSn()
                + ",category=" + application.getCategorySn()
                + ",permissionStatus=" + application.getPermissionStatusCode()
                + ",permissionUse=" + application.getPermissionUseYn();
    }
}
