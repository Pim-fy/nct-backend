package nct.review.dto;

import lombok.Data;

// @ai_generated
/** 거래 참여 여부를 확인한 뒤 내 리뷰 상태를 계산하기 위한 내부 조회 결과다. */
@Data
public class TradeReviewStateSource {
    private Long tradeId;
    private String tradeStatusCode;
    private Long reviewId;
    private String reviewUseYn;
    private Integer rating;
    private String content;
}
