package nct.servicerequest.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7: F-SVC-002 답변에 따른 필드 숨김·비활성 규칙 계약. */
@Getter
@Setter
@NoArgsConstructor
public class ServiceRequestFormRule {

    private Long ruleSn;
    private Long targetStepSn;
    private Long targetFieldSn;
    private Long sourceStepSn;
    private Long sourceFieldStepSn;
    private Long sourceFieldSn;
    private Long compareStepOptionSn;
    private Long compareFieldOptionSn;
    private String sourceStepKey;
    private String sourceFieldStepKey;
    private String sourceFieldKey;
    private String sourceFieldLabel;
    private String compareValue;
    private String operator;
    private String action;
    private Integer sortNo;
}
