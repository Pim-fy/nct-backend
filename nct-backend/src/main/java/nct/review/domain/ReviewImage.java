package nct.review.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [리뷰 - REVIEW_IMAGE 행 모델]
 * - 리뷰 하나에 여러 장의 사진이 붙을 수 있다 (PRODUCT_IMAGE와 동일 패턴).
 * - flSn은 FILES 테이블의 FK — ReviewService가 storeImage() 반환값에서 가져온다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewImage {

    private Long rvwImgSn;
    private Long rvwSn;
    private Long flSn;
    private Integer rvwImgSortNo;
}
