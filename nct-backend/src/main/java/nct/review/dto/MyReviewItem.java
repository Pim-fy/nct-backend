package nct.review.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [리뷰 - "내가 작성한 리뷰" 한 행]
 * - ReviewMapper 조회 결과(rating/content/title/dealType/partyLabel/partyName/completedDate)에
 *   photos만 서비스 계층에서 FileStorageService로 채워 넣는다 (SQL로는 JOIN하지 않음 - 리뷰마다
 *   N+1 쿼리가 되지만 목록이 최대 100건이라 지금 단계에서는 허용 가능한 수준으로 판단).
 */
@Getter
@Builder(toBuilder = true) // toBuilder: getMyReviews()에서 photos만 나중에 채워 넣을 때 사용
@NoArgsConstructor
@AllArgsConstructor
public class MyReviewItem {

    private Long id;           // RVW_SN
    private Long reviewId;     // 수정·삭제 대상 리뷰
    private Long auctionId;    // 물건 거래의 정식 화면 경로 식별자 (서비스 거래는 null)
    // @ai_generated (담당자1 황희준, 2026-08-07, 조율 대기): SQL은 AUCTION을 직접 JOIN하지 않고
    // 이 값만 채우고, ReviewService가 AuctionService 계약으로 auctionId를 채운다. API 응답에는
    // 노출하지 않는다.
    @JsonIgnore
    private Long productId;
    private Long tradeId;
    private int rating;
    private String content;
    private String title;
    private String dealType;
    private String partyLabel;
    private String partyName;
    private String completedDate; // 리뷰 작성일 (yyyy-MM-dd)
    private String thumbnail;    // 물건거래: 상품 대표 이미지 경로 / 서비스거래: SVC_REQ_IMAGE 첫 번째 이미지 API 경로
    private List<String> photos;
}
