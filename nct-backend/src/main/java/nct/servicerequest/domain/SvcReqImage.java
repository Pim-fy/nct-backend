package nct.servicerequest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [서비스 요청 - SVC_REQ_IMAGE 행 모델] (F-SVC-001)
 * - 요청서 하나에 여러 장(최대 5장)이 붙을 수 있다. 대표이미지 개념은 없음(단순 다중 첨부).
 * - 등록 폼에서 넘어온 flSn 목록의 순서를 그대로 정렬순서(svcReqImgSortNo)로 쓴다.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SvcReqImage {

    private Long svcReqImgSn;
    private Long svcReqSn;
    private Long flSn;
    private Integer svcReqImgSortNo;
}
