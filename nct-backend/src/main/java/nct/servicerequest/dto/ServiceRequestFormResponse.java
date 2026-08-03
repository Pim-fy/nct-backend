package nct.servicerequest.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 담당자 7: F-SVC-002 서비스 요청 동적 폼 공개 조회 계약.
 * 화면은 이 응답을 기존 위저드 모양으로 변환해 사용한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ServiceRequestFormResponse {

    private Long formTemplateSn;
    private Long catSn;
    private String catNm;
    private Integer formVersion;
    private String firstStepKey;
    private String subtitle;
    private String uiMetaJson;
    private List<ServiceRequestFormStep> steps = new ArrayList<>();
}
