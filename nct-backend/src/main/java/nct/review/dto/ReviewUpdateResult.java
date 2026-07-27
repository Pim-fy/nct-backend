package nct.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** [리뷰 - 수정 결과 응답 DTO] */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewUpdateResult {

    private Long id;          // 수정된 RVW_SN
    private int rating;
    private int addedPhotoCount; // 이번 수정 요청에서 새로 추가된 사진 수 (기존 첨부 사진은 그대로 유지)
}
