package nct.servicerequest.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7: F-SVC-002 동적 폼 입력 필드와 선택지·표시 규칙 계약. */
@Getter
@Setter
@NoArgsConstructor
public class ServiceRequestFormField {

    private Long fieldSn;
    private Long stepSn;
    private String fieldKey;
    private String label;
    private String type;
    private String placeholder;
    private String description;
    private String requiredYn;
    private String requireDigitYn;
    private String sensitiveYn;
    private String publicYn;
    private Integer maxSelections;
    private Integer sortNo;
    private String uiMetaJson;
    private List<ServiceRequestFormOption> options = new ArrayList<>();
    private List<ServiceRequestFormRule> rules = new ArrayList<>();
}
