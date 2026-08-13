package nct.trade.port;

/** 담당자 7 · 신고 제재: 제재로 보류된 거래를 충돌이 없을 때 이전 상태로 복구합니다. */
public record MemberTradeRestoreCommand(
        Long tradeSn,
        String previousStatusCode,
        Long remainingSeconds,
        boolean settlementHeld,
        Long adminUserSn,
        String reason) {
}
