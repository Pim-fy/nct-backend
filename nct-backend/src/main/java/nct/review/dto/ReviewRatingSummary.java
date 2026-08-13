package nct.review.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 담당자 3 제공·담당자 7 소비 · 회원이 받은 활성 리뷰 전체의 산술 평균과 건수다. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRatingSummary {
    private BigDecimal averageScore;
    private long reviewCount;
}
