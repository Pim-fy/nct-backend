package nct.trade.port;

import java.time.LocalDateTime;

/** 담당자 7 · F-OPS-020: 실제 보류된 거래와 상대 회원 정보입니다. */
public record RestrictedTrade(
        Long tradeSn,
        Long counterpartUserSn,
        String previousStatusCode,
        LocalDateTime previousDeadlineAt,
        Long remainingSeconds,
        boolean settlementHeld) {
}
