package nct.ops.servicequery.dto;

/** 담당자 7 · F-OPS-021: 관리자 견적 무효화 결과입니다. */
public record AdminQuoteOperationResponse(
        Long serviceRequestId,
        Long quoteId,
        String previousStatusCode,
        String statusCode,
        boolean changed) {
}
