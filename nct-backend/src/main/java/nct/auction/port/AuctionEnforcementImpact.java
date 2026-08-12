package nct.auction.port;

import java.time.LocalDateTime;

/** 담당자 7 · 신고 제재: 경매별 적용 결과와 임시정지 복구값입니다. */
public record AuctionEnforcementImpact(
        Long auctionId,
        String roleCode,
        String actionCode,
        String previousStatusCode,
        LocalDateTime previousStartAt,
        LocalDateTime previousDeadlineAt,
        Long remainingStartSeconds,
        Long remainingSeconds,
        String result) {
}
