package nct.quote.port;

/** 담당자 7 · 신고 제재: 기간 보류·영구 철회·검토 보류한 견적 결과입니다. */
public record QuoteEnforcementImpact(
        Long quoteId,
        String roleCode,
        String actionCode,
        String previousStatusCode,
        String result) {
}
