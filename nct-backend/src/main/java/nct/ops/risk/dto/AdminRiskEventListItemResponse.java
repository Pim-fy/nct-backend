package nct.ops.risk.dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 담당자 7 · F-OPS-011: 관리자 목록 한 줄에 필요한 안전한 위험 이벤트 정보입니다. */
@Data
public class AdminRiskEventListItemResponse {
    private Long riskEventId;
    private String typeCode;
    private String typeName;
    private String referenceTypeCode;
    private Long referenceSn;
    private String content;
    private String processedYn;
    private LocalDateTime registeredAt;
}
