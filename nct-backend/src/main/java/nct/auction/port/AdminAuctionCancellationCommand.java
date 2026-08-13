package nct.auction.port;

/**
 * 판매자 취소 요청 없이 관리자가 경매를 직접 취소할 때 사용하는 경매 도메인 명령입니다.
 */
public record AdminAuctionCancellationCommand(
        Long auctionId,
        Long adminUserId,
        String reason,
        String requestId,
        Long sourceReportSn) {

    public AdminAuctionCancellationCommand(
            Long auctionId,
            Long adminUserId,
            String reason,
            String requestId) {
        this(auctionId, adminUserId, reason, requestId, null);
    }
}
