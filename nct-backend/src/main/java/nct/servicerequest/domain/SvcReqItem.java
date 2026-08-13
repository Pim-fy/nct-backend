package nct.servicerequest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [서비스 요청 - SVC_REQ_ITEM 행 모델] (F-SVC-001)
 * - 기존 문자열 행과 F-SVC-002 구조화 답변 행을 함께 표현한다.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SvcReqItem {

    private Long svcReqItmSn;
    private Long svcReqSn;
    private Long formTemplateSn;
    private Long stepSn;
    private Long fieldSn;
    private Long stepOptionSn;
    private Long fieldOptionSn;
    private String svcReqItmCn;
    private String value;
    private String encryptedValue;
    private String structuredYn;
    private String publicYn;
    private Integer svcReqItmSortNo;
}
