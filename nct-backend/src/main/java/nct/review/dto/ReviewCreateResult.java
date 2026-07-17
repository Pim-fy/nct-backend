package nct.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** [리뷰 - 등록 결과 응답 DTO] */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCreateResult {

    private Long id;       // 생성된 RVW_SN
    private Long tradeId;
    private int rating;
    private int photoCount;
}
