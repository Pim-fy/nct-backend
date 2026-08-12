package nct.ops.servicequery.dto;

/** 담당자 7 · F-OPS-021: 관리자 견적 요청 취소 결과입니다. */
public record AdminServiceRequestOperationResponse(
        Long serviceRequestId,
        String previousStatusCode,
        String statusCode,
        int invalidatedQuoteCount,
        boolean changed) {
}
