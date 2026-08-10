package nct.review.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

// @ai_generated
/** 기존 reviewId 경로를 auctionId 기반 정식 경로로 변환하는 최소 조회 응답이다. */
@Data
public class ReviewRouteContext {
    private Long reviewId;
    private Long tradeId;
    private Long auctionId;
    // @ai_generated (담당자1 황희준, 2026-08-07, 조율 대기): SQL은 AUCTION을 직접 JOIN하지 않고
    // 이 값만 채우고, ReviewService가 AuctionService 계약으로 auctionId를 채운다. API 응답에는
    // 노출하지 않는다.
    @JsonIgnore
    private Long productId;
}
