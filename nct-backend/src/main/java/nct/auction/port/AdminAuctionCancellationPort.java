package nct.auction.port;

/**
 * 관리자 경매 관리 화면이 판매자 취소 요청과 무관하게 경매를 취소하는 공개 계약입니다.
 */
public interface AdminAuctionCancellationPort {

    AdminAuctionCancellationResult cancel(AdminAuctionCancellationCommand command);
}
