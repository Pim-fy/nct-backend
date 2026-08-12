package nct.auction.port;

import java.time.LocalDateTime;

/** 담당자 7 · 신고 제재: 회원 연관 경매를 중지하거나 영구정지에 맞게 취소하는 명령입니다. */
public record MemberAuctionEnforcementCommand(
        Long userSn,
        Long adminUserSn,
        String reason,
        String requestId,
        LocalDateTime releaseAt,
        Long sourceReportSn) {

    public MemberAuctionEnforcementCommand(
            Long userSn,
            Long adminUserSn,
            String reason,
            String requestId,
            LocalDateTime releaseAt) {
        this(userSn, adminUserSn, reason, requestId, releaseAt, null);
    }
}
