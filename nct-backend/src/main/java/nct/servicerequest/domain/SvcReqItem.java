package nct.servicerequest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [서비스 요청 - SVC_REQ_ITEM 행 모델] (F-SVC-001)
 * - 요청서 하나에 여러 줄의 요청 조건/체크리스트 항목이 붙는다.
 * - 등록 폼에서 넘어온 문자열 목록의 순서를 그대로 정렬순서(svcReqItmSortNo)로 쓴다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SvcReqItem {

    private Long svcReqItmSn;
    private Long svcReqSn;
    private String svcReqItmCn;
    private Integer svcReqItmSortNo;
}
