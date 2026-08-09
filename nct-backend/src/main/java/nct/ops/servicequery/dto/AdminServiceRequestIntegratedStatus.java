package nct.ops.servicequery.dto;

/** 담당자 7 · F-OPS-021: 원본 상태를 변경하지 않고 관리자 조회에만 사용하는 통합 상태입니다. */
public record AdminServiceRequestIntegratedStatus(String code, String label) {
}
