package nct.ops.servicequery.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.notification.domain.NotificationAudience;
import nct.notification.domain.NotificationDomain;
import nct.notification.domain.NotificationType;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.servicequery.dto.AdminQuoteOperationResponse;
import nct.ops.servicequery.dto.AdminServiceRequestOperationResponse;
import nct.ops.servicequery.dto.AdminServiceRequestVisibilityResponse;
import nct.quote.port.AdminQuoteModerationPort;
import nct.quote.port.AdminQuoteModerationPort.AdminQuoteBulkModerationResult;
import nct.quote.port.AdminQuoteModerationPort.AdminQuoteModerationResult;
import nct.servicerequest.port.AdminServiceRequestCommandPort;
import nct.servicerequest.port.AdminServiceRequestCommandPort.AdminServiceRequestCommandResult;
import nct.servicerequest.port.AdminServiceRequestCommandPort.AdminServiceRequestVisibilityResult;

/**
 * 담당자 7 · F-OPS-021/F-OPS-015: 거래 생성 전 견적 요청·견적에만 제한적인 운영 명령을 적용합니다.
 * 매칭완료 이후 거래·신고·제재 상태는 이 서비스에서 변경하지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class AdminServiceRequestOperationService {

    private final AdminServiceRequestCommandPort serviceRequestCommandPort;
    private final AdminQuoteModerationPort quoteModerationPort;
    private final AuditLogPort auditLogPort;
    private final NotificationService notificationService;

    @Transactional
    public AdminServiceRequestOperationResponse cancelRequest(
            Long serviceRequestId,
            String reason,
            String requestId,
            Long adminUserId) {
        String normalizedReason = validate(serviceRequestId, reason, requestId, adminUserId);
        AdminServiceRequestCommandResult request = serviceRequestCommandPort.cancelOpen(
                serviceRequestId, adminUserId);
        if (!request.changed()) {
            return new AdminServiceRequestOperationResponse(
                    serviceRequestId,
                    request.previousStatusCode(),
                    request.statusCode(),
                    0,
                    false);
        }
        AdminQuoteBulkModerationResult quotes = quoteModerationPort.invalidateActiveQuotes(
                serviceRequestId, adminUserId);
        auditLogPort.record(new AuditLogCommand(
                "STATUS_CHANGE",
                String.valueOf(adminUserId),
                RefType.SERVICE_REQUEST.getCode(),
                serviceRequestId,
                normalizedReason,
                "requestStatus=" + request.previousStatusCode(),
                "requestStatus=" + request.statusCode()
                        + ",invalidatedQuotes=" + quotes.changedCount(),
                requestId.trim()));
        notificationService.notify(
                request.requesterUserId(),
                NotificationType.OPS,
                NotificationDomain.SERVICE,
                "견적 요청이 관리자에 의해 취소되었습니다",
                "견적 요청 #" + serviceRequestId + "이(가) 취소되었습니다. 사유: " + normalizedReason,
                RefType.SERVICE_REQUEST,
                serviceRequestId);
        for (Long providerUserId : quotes.providerUserIds()) {
            notificationService.notify(
                    providerUserId,
                    NotificationType.OPS,
                    NotificationDomain.SERVICE,
                    NotificationAudience.PROVIDER,
                    "제출한 견적의 요청이 취소되었습니다",
                    "견적 요청 #" + serviceRequestId + "이(가) 관리자에 의해 취소되었습니다.",
                    RefType.SERVICE_REQUEST,
                    serviceRequestId);
        }
        return new AdminServiceRequestOperationResponse(
                serviceRequestId,
                request.previousStatusCode(),
                request.statusCode(),
                quotes.changedCount(),
                true);
    }

    @Transactional
    public AdminQuoteOperationResponse invalidateQuote(
            Long serviceRequestId,
            Long quoteId,
            String reason,
            String requestId,
            Long adminUserId) {
        String normalizedReason = validate(serviceRequestId, reason, requestId, adminUserId);
        if (quoteId == null || quoteId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        AdminQuoteModerationResult result = quoteModerationPort.invalidateActiveQuote(
                serviceRequestId, quoteId, adminUserId);
        if (!result.changed()) {
            return response(result);
        }
        auditLogPort.record(new AuditLogCommand(
                "STATUS_CHANGE",
                String.valueOf(adminUserId),
                RefType.QUOTE.getCode(),
                quoteId,
                normalizedReason,
                "quoteStatus=" + result.previousStatusCode(),
                "quoteStatus=" + result.statusCode(),
                requestId.trim(),
                RefType.SERVICE_REQUEST.getCode(),
                serviceRequestId));
        notificationService.notify(
                result.providerUserId(),
                NotificationType.OPS,
                NotificationDomain.SERVICE,
                NotificationAudience.PROVIDER,
                "제출한 견적이 관리자에 의해 무효화되었습니다",
                "견적 #" + quoteId + "이(가) 무효화되었습니다. 사유: " + normalizedReason,
                RefType.QUOTE,
                quoteId);
        return response(result);
    }

    @Transactional
    public AdminServiceRequestVisibilityResponse changeVisibility(
            Long serviceRequestId,
            boolean visible,
            String reason,
            String requestId,
            Long adminUserId) {
        String normalizedReason = validate(serviceRequestId, reason, requestId, adminUserId);
        AdminServiceRequestVisibilityResult result = serviceRequestCommandPort.changeVisibility(
                serviceRequestId, visible, adminUserId);
        if (!result.changed()) {
            return visibilityResponse(result);
        }
        auditLogPort.record(new AuditLogCommand(
                "STATUS_CHANGE",
                String.valueOf(adminUserId),
                RefType.SERVICE_REQUEST.getCode(),
                serviceRequestId,
                normalizedReason,
                "visible=" + result.previousVisible(),
                "visible=" + result.visible(),
                requestId.trim()));
        notificationService.notify(
                result.requesterUserId(),
                NotificationType.OPS,
                NotificationDomain.SERVICE,
                visible ? "견적 요청이 다시 공개되었습니다" : "견적 요청이 운영 숨김 처리되었습니다",
                "견적 요청 #" + serviceRequestId + "의 노출 상태가 변경되었습니다. 사유: "
                        + normalizedReason,
                RefType.SERVICE_REQUEST,
                serviceRequestId);
        return visibilityResponse(result);
    }

    private AdminQuoteOperationResponse response(AdminQuoteModerationResult result) {
        return new AdminQuoteOperationResponse(
                result.serviceRequestId(),
                result.quoteId(),
                result.previousStatusCode(),
                result.statusCode(),
                result.changed());
    }

    private AdminServiceRequestVisibilityResponse visibilityResponse(
            AdminServiceRequestVisibilityResult result) {
        return new AdminServiceRequestVisibilityResponse(
                result.serviceRequestId(),
                result.previousVisible(),
                result.visible(),
                result.changed());
    }

    private String validate(
            Long serviceRequestId,
            String reason,
            String requestId,
            Long adminUserId) {
        if (serviceRequestId == null || serviceRequestId <= 0
                || adminUserId == null || adminUserId <= 0
                || reason == null || reason.isBlank() || reason.trim().length() > 1000
                || requestId == null || requestId.isBlank() || requestId.trim().length() > 100) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return reason.trim();
    }
}
