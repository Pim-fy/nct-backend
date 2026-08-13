package nct.auction.port;

/** 담당자 7 · F-OPS-003: 관리자 수동 일시중지·재개의 경매 및 행위자 계약입니다. */
public record AdminAuctionPauseCommand(Long auctionId, Long adminUserId) {
}
