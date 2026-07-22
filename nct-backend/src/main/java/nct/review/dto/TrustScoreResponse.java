package nct.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [리뷰 - 신뢰지표 응답] (F-COM-009~010, 담당자4 정민재 소비)
 * - GET /api/reviews/trust/{usrSn} 응답.
 * - totalScore/goodsScore/serviceScore 는 리뷰가 없으면 null (JSON 미포함 — ApiResponse의 NON_NULL 정책).
 * - hasReviews: totalCount > 0, 서비스 계층에서 세팅.
 * - toBuilder: 서비스에서 usrSn·hasReviews 세팅 시 사용.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TrustScoreResponse {

    private Long usrSn;
    private Double totalScore;    // 소수점 1자리 (AVG ROUND 1), 리뷰 없음→null
    private int totalCount;
    private Double goodsScore;
    private int goodsCount;
    private Double serviceScore;
    private int serviceCount;
    private boolean hasReviews;
}
