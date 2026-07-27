package nct.review.dto;

import java.util.List;

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
    private Long tradeId;
    private int rating;
    private String content;
    private String title;
    private String dealType;
    private String partyLabel;
    private String partyName;
    private String completedDate; // 리뷰 작성일 (yyyy-MM-dd)
    private List<String> photos;
}
