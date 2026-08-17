package nct.ops.operation.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.auction.dto.AuctionDetailResponse;
import nct.auction.port.AdminAuctionCancellationCommand;
import nct.auction.port.AdminAuctionCancellationPort;
import nct.auction.port.AdminAuctionCancellationResult;
import nct.auction.port.AdminAuctionPauseCommand;
import nct.auction.port.AdminAuctionPausePort;
import nct.auction.port.AdminAuctionPauseResult;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.notification.domain.NotificationDomain;
import nct.notification.domain.NotificationType;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.operation.dto.AdminAuctionOverviewResponse;
import nct.ops.operation.dto.AdminProductVisibilityResult;
import nct.product.dto.ProductResponse;
import nct.product.service.ProductService;

/**
 * 담당자 7 · F-OPS-003/004/015: 관리자 강제취소를 경매 도메인의 안전한 취소 계약에 연결합니다.
 * BID·포인트·거래·경매 테이블을 직접 수정하지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class AdminAuctionOperationService {

    private final AdminAuctionQueryService queryService;
    private final AdminAuctionCancellationPort cancellationPort;
    private final AdminAuctionPausePort pausePort;
    private final ProductService productService;
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
                    auctionNotificationContent(overview, auctionSn, normalizedReason),
                    RefType.AUCTION,
                    auctionSn);
        }
        return result;
    }

    /** 담당자 7 · F-OPS-003: 상품 삭제 없이 공개 노출만 숨김·복구합니다. */
    @Transactional
    public AdminProductVisibilityResult changeProductVisibility(
            Long auctionSn,
            boolean visible,
            String reason,
            String requestId,
            Long adminUserSn) {
        String normalizedReason = validate(auctionSn, reason, requestId, adminUserSn);
        AdminAuctionOverviewResponse overview = queryService.getAuctionOverview(auctionSn);
        ProductResponse product = overview.getProduct();
        if (product == null || product.getPrdSn() == null) {
            throw new CustomException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        boolean changed = productService.changeVisibilityForAdmin(
                product.getPrdSn(), visible, adminUserSn);
        if (!changed) {
            return new AdminProductVisibilityResult(product.getPrdSn(), visible, false);
        }

        String before = visible ? "productVisible=false" : "productVisible=true";
        String after = "productVisible=" + visible;
        auditLogPort.record(new AuditLogCommand(
                "STATUS_CHANGE",
                String.valueOf(adminUserSn),
                RefType.PRODUCT.getCode(),
                product.getPrdSn(),
                normalizedReason,
                before,
                after,
                requestId.trim(),
                RefType.AUCTION.getCode(),
                auctionSn));
        if (product.getUsrSn() != null) {
            notificationService.notify(
                    product.getUsrSn(),
                    NotificationType.AUCTION,
                    NotificationDomain.AUCTION,
                    visible ? "상품 노출이 복구되었습니다." : "상품이 숨김 처리되었습니다.",
                    "상품 #" + product.getPrdSn() + "의 공개 노출 상태가 변경되었습니다. 사유: "
                            + normalizedReason,
                    RefType.PRODUCT,
                    product.getPrdSn());
        }
        return new AdminProductVisibilityResult(product.getPrdSn(), visible, true);
    }

    /** 담당자 7 · F-OPS-003: 진행 중 경매를 수동 일시중지합니다. */
    @Transactional
    public AdminAuctionPauseResult pause(
            Long auctionSn,
            String reason,
            String requestId,
            Long adminUserSn) {
        return changePauseState(auctionSn, true, reason, requestId, adminUserSn);
    }

    /** 담당자 7 · F-OPS-003: 수동 일시중지한 경매를 남은 시간 기준으로 재개합니다. */
    @Transactional
    public AdminAuctionPauseResult resume(
            Long auctionSn,
            String reason,
            String requestId,
            Long adminUserSn) {
        return changePauseState(auctionSn, false, reason, requestId, adminUserSn);
    }

    private AdminAuctionPauseResult changePauseState(
            Long auctionSn,
            boolean pause,
            String reason,
            String requestId,
            Long adminUserSn) {
        String normalizedReason = validate(auctionSn, reason, requestId, adminUserSn);
        AdminAuctionOverviewResponse overview = queryService.getAuctionOverview(auctionSn);
        AdminAuctionPauseResult result = pause
                ? pausePort.pause(new AdminAuctionPauseCommand(auctionSn, adminUserSn))
                : pausePort.resume(new AdminAuctionPauseCommand(auctionSn, adminUserSn));
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
        if (result.sellerUserId() != null) {
            notificationService.notify(
                    result.sellerUserId(),
                    NotificationType.AUCTION,
                    NotificationDomain.AUCTION,
                    pause ? "경매가 일시중지되었습니다." : "경매가 다시 시작되었습니다.",
                    auctionNotificationContent(overview, auctionSn, normalizedReason),
                    RefType.AUCTION,
                    auctionSn);
        }
        return result;
    }

    /** 담당자 7 · ISSUE-T7-009: 알림에는 상품명과 경매 번호를 함께 표시합니다. */
    private String auctionNotificationContent(
            AdminAuctionOverviewResponse overview,
            Long auctionSn,
            String reason) {
        String displayName = null;
        if (overview != null && overview.getProduct() != null) {
            displayName = overview.getProduct().getPrdNm();
        }
        if ((displayName == null || displayName.isBlank())
                && overview != null && overview.getAuction() != null) {
            displayName = overview.getAuction().getTitle();
        }
        String normalizedName = displayName == null || displayName.isBlank()
                ? "해당 경매"
                : displayName.trim();
        return normalizedName + " · 경매 #" + auctionSn + " · 사유: " + reason;
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
