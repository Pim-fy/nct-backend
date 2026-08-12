package nct.auction.port;

/** 담당자 7 · 신고 제재: 운영보류 경매를 충돌 없이 이전 상태로 복구하는 명령입니다. */
public record AuctionEnforcementRestoreCommand(
        Long auctionId,
        String previousStatusCode,
        Long remainingStartSeconds,
        Long remainingSeconds,
        Long adminUserSn) {
}
