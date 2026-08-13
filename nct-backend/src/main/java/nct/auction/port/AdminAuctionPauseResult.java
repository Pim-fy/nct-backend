package nct.auction.port;

/** 담당자 7 · F-OPS-003: 관리자 수동 일시중지·재개 전후 상태입니다. */
public record AdminAuctionPauseResult(
        Long auctionId,
        Long sellerUserId,
        String previousStatusCode,
        String statusCode,
        boolean changed) {
}
