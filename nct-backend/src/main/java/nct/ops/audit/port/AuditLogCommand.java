package nct.ops.audit.port;

/**
 * 담당자 7 · F-OPS-015: 운영 행위의 감사 기록 요청입니다.
 * 민감정보 원문은 넣지 않고, 대상·사유·변경 전후의 마스킹된 요약만 전달합니다.
 */
public record AuditLogCommand(
        String actionCode, String actorId, String referenceTypeCode, Long referenceSn,
        String reason, String beforeSummary, String afterSummary, String requestId) {
}
