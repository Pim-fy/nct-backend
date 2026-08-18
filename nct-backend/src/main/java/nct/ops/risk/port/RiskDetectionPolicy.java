package nct.ops.risk.port;

/** 담당자 7 · REQ-OPS-011: SYSTEM_SETTING에서 읽는 리스크 자동 판정 기준입니다. */
public record RiskDetectionPolicy(
        int tradeReportCount,
        int tradeReportWindowMinutes,
        int settlementHoldDays,
        int repeatReportCount,
        int repeatReportWindowDays,
        int adminLoginFailCount,
        int adminLoginFailWindowMinutes) {
}
