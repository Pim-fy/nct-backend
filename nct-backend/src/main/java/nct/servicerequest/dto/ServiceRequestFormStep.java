package nct.servicerequest.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7: F-SVC-002 카테고리별 질문 단계와 다음 단계 계약. */
@Getter
@Setter
@NoArgsConstructor
public class ServiceRequestFormStep {

    private Long stepSn;
    // 여러 템플릿을 한 번에 조회(findStepsByTemplates)할 때 결과를 템플릿별로 묶는 데 사용 —
    // 단건 조회(findSteps)에서는 항상 같은 값이라 안 채워도 무방.
    private Long formTemplateSn;
    private String stepKey;
    private String title;
    private String description;
    private String type;
    private String nextStepKey;
    private String sensitiveYn;
    private String publicYn;
    private Integer sortNo;
    private List<ServiceRequestFormOption> options = new ArrayList<>();
    private List<ServiceRequestFormField> fields = new ArrayList<>();
}
