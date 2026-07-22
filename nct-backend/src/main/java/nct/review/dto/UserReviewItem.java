package nct.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [리뷰 - 특정 회원이 받은 리뷰 한 행] (F-COM-008, 담당자4 정민재 소비)
 * - GET /api/reviews/user/{usrSn} 응답 원소.
 * - reviewerName은 서비스 계층에서 마스킹 후 세팅된다 (SQL에서는 USR_NM 원본으로 조회).
 * - toBuilder: 서비스에서 reviewerName 마스킹 시 사용.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class UserReviewItem {

    private Long reviewId;
    private int rating;
    private String content;
    private String createdDate;    // yyyy-MM-dd
    private String dealType;       // "goods" | "service"
    private Long tradeId;
    private Long productId;        // goods → PRD_SN, service → SVC_REQ_SN
    private String productTitle;   // goods → PRD_NM, service → SVC_REQ_TTL
    private String reviewerName;   // 마스킹 완료 (홍*동)
    private Long reviewerUsrSn;
}
