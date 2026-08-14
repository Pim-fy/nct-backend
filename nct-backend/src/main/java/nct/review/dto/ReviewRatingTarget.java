package nct.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 담당자 7 연동 · 리뷰 수정·삭제 후 제공자 평점 캐시를 다시 계산할 내부 대상이다. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRatingTarget {
    private Long receiverUserSn;
}
