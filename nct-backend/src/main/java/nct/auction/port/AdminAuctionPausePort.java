package nct.auction.port;

/** 담당자 7 · F-OPS-003: AUCTION 테이블을 우회하지 않는 관리자 일시중지 계약입니다. */
public interface AdminAuctionPausePort {

    AdminAuctionPauseResult pause(AdminAuctionPauseCommand command);

    AdminAuctionPauseResult resume(AdminAuctionPauseCommand command);
}
