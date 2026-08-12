package nct.auction.port;

/** 관리자 직접 취소 전후의 경매 상태입니다. */
public record AdminAuctionCancellationResult(
        Long auctionId,
        String previousStatusCode,
        String statusCode,
        boolean changed) {
}
