package nct.review.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import nct.review.constant.MyReviewStatus;

// @ai_generated
/** 거래 상세에서 현재 로그인 사용자의 리뷰 상태와 작성 내용을 함께 반환한다. */
@Getter
@Builder
public class MyTradeReviewResponse {
    private MyReviewStatus status;
    private Long tradeId;
    private Long reviewId;
    private Integer rating;
    private String content;
    @Builder.Default
    private List<String> photos = List.of();
}
