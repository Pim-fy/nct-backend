package nct.trade.port;

import java.time.LocalDateTime;

/** 담당자 7 - F-OPS-007: 영구정지가 거래에 적용된 결과입니다. */
public record TradeEnforcementImpact(
        Long tradeSn,
        Long counterpartUserSn,
        String actionCode,
        String previousStatusCode,
        LocalDateTime previousDeadlineAt,
        Long remainingSeconds,
        boolean settlementHeld,
        String result) {
}
