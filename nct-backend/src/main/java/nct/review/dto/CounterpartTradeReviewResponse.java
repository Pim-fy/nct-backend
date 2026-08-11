package nct.review.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import nct.review.constant.CounterpartReviewStatus;

// @ai_generated
/** 거래 상세에서 상대방이 나에 대해 작성한 리뷰 상태와 내용을 함께 반환한다. */
@Getter
@Builder
public class CounterpartTradeReviewResponse {
    private CounterpartReviewStatus status;
    private Long tradeId;
    private Integer rating;
    private String content;
    @Builder.Default
    private List<String> photos = List.of();
}
