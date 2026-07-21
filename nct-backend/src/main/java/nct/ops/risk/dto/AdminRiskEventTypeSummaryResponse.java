package nct.ops.risk.dto;

import lombok.Data;

/** 담당자 7 · F-OPS-011: 대시보드에서 재사용하는 위험 유형별 건수입니다. */
@Data
public class AdminRiskEventTypeSummaryResponse {
    private String typeCode;
    private String typeName;
    private long count;
}
