package nct.ops.risk.event;

/** 담당자 7 · REQ-OPS-011: 신고 커밋 뒤 자동 판정을 요청하는 내부 이벤트입니다. */
public record ReportCreatedRiskSignal(
        long reportSn,
        Long reportedUserSn,
        boolean tradeReport) {
}
