package nct.ops.operation.port;

/**
 * 담당자 7 · F-OPS-004: 판매자 취소 승인/반려 요청입니다.
 * TRADE와 TRADE_STATUS_HIST를 소유한 담당자 4 서비스가 상태·사유·감사 기록을 처리합니다.
 */
public record SellerCancellationDecisionCommand(
        Long tradeSn, SellerCancellationDecision decision, String reason, String adminId, String requestId) {
}
