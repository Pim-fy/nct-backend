package nct.review.port;

import nct.review.dto.ReviewRatingSummary;

/** 담당자 3 제공·담당자 7 소비 · F-COM-009 통합 평점 읽기 경계다. */
public interface ReviewRatingReader {
    ReviewRatingSummary read(long userSn);
}
