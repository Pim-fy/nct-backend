package nct.ops.operation.port;

/**
 * 담당자 7 · F-OPS-007: 관리자 신고 처리 요청입니다.
 * ABUSE_REPORT를 소유한 담당자 5 서비스가 이 값으로 상태·사유를 한 번만 변경합니다.
 */
public record AdminReportDecisionCommand(
        Long reportSn, AdminReportDecision decision, String reason, String adminId, String requestId) {
}
