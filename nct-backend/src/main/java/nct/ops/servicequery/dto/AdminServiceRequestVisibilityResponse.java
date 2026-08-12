package nct.ops.servicequery.dto;

/** 담당자 7 · F-OPS-021: 관리자 견적 요청 숨김·복구 결과입니다. */
public record AdminServiceRequestVisibilityResponse(
        Long serviceRequestId,
        boolean previousVisible,
        boolean visible,
        boolean changed) {
}
