package nct.servicerequest.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7: F-SVC-002 단계 또는 입력 필드의 허용 선택지 계약. */
@Getter
@Setter
@NoArgsConstructor
public class ServiceRequestFormOption {

    private Long optionSn;
    private Long stepSn;
    private Long fieldSn;
    private String optionKey;
    private String value;
    private String label;
    private String subtitle;
    private String nextStepKey;
    private Integer sortNo;
}
