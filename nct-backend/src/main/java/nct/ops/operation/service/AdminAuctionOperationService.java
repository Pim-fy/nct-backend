package nct.ops.operation.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.auction.dto.AuctionDetailResponse;
import nct.auction.port.AdminAuctionCancellationCommand;
import nct.auction.port.AdminAuctionCancellationPort;
import nct.auction.port.AdminAuctionCancellationResult;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.notification.domain.NotificationDomain;
import nct.notification.domain.NotificationType;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.operation.dto.AdminAuctionOverviewResponse;

/**
 * 담당자 7 · F-OPS-003/004/015: 관리자 강제취소를 경매 도메인의 안전한 취소 계약에 연결합니다.
 * BID·포인트·거래·경매 테이블을 직접 수정하지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class AdminAuctionOperationService {

    private final AdminAuctionQueryService queryService;
    private final AdminAuctionCancellationPort cancellationPort;
    private final AuditLogPort auditLogPort;
    private final NotificationService notificationService;

    @Transactional
    public AdminAuctionCancellationResult forceCancel(
            Long auctionSn,
            String reason,
            String requestId,
            Long adminUserSn) {
        String normalizedReason = validate(auctionSn, reason, requestId, adminUserSn);
        AdminAuctionOverviewResponse overview = queryService.getAuctionOverview(auctionSn);
        AuctionDetailResponse auction = overview.getAuction();
        AdminAuctionCancellationResult result = cancellationPort.cancel(
                new AdminAuctionCancellationCommand(
                        auctionSn,
                        adminUserSn,
                        normalizedReason,
                        requestId.trim()));
        if (!result.changed()) {
            return result;
        }

        auditLogPort.record(new AuditLogCommand(
                "STATUS_CHANGE",
                String.valueOf(adminUserSn),
                RefType.AUCTION.getCode(),
                auctionSn,
                normalizedReason,
                "auctionStatus=" + result.previousStatusCode(),
                "auctionStatus=" + result.statusCode(),
                requestId.trim()));
        if (auction != null && auction.getSellerId() != null) {
            notificationService.notify(
                    auction.getSellerId(),
                    NotificationType.AUCTION,
                    NotificationDomain.AUCTION,
                    "관리자에 의해 경매가 취소되었습니다",
                    "경매 #" + auctionSn + "이(가) 취소되었습니다. 사유: " + normalizedReason,
                    RefType.AUCTION,
                    auctionSn);
        }
        return result;
    }

    private String validate(
            Long auctionSn,
            String reason,
            String requestId,
            Long adminUserSn) {
        if (auctionSn == null || auctionSn <= 0
                || adminUserSn == null || adminUserSn <= 0
                || reason == null || reason.isBlank() || reason.trim().length() > 1000
                || requestId == null || requestId.isBlank() || requestId.trim().length() > 100) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return reason.trim();
    }
}
