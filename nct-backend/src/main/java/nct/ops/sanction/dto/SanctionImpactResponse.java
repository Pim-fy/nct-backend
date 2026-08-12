package nct.ops.sanction.dto;

/** 담당자 7 - F-OPS-007: 신고 제재가 각 업무에 적용된 결과입니다. */
public record SanctionImpactResponse(
        String referenceTypeCode,
        Long referenceSn,
        String roleCode,
        String actionCode,
        String statusCode,
        String result) {
}
