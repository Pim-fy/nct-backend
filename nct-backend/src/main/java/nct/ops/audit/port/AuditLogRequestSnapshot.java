package nct.ops.audit.port;

/** 담당자 7 · REQ-OPS-003: 감사로그에 이미 기록된 운영 요청의 멱등성 판단 정보입니다. */
public record AuditLogRequestSnapshot(
        Long actorUserSn,
        String referenceTypeCode,
        Long referenceSn,
        String beforeSummary,
        String afterSummary) {
}
