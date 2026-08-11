package nct.review.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.review.dto.CounterpartTradeReviewResponse;
import nct.review.dto.MyTradeReviewResponse;
import nct.review.service.ReviewService;

// @ai_generated
/** 거래 상세가 소비하는 현재 로그인 사용자의 리뷰 상태 API다. */
@RestController
@RequestMapping("/api/trades/{tradeId}/reviews")
@RequiredArgsConstructor
public class TradeReviewController {

    private final ReviewService reviewService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyTradeReviewResponse>> getMyTradeReview(
            @PathVariable("tradeId") long tradeId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        long userId = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(reviewService.getMyTradeReview(userId, tradeId)));
    }

    @GetMapping("/counterpart")
    public ResponseEntity<ApiResponse<CounterpartTradeReviewResponse>> getCounterpartTradeReview(
            @PathVariable("tradeId") long tradeId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        long userId = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(reviewService.getCounterpartTradeReview(userId, tradeId)));
    }
}
