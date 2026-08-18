package nct.review.port;

import nct.review.dto.ReviewRatingSummary;

/** 담당자 3 제공·담당자 7 소비 · F-COM-009/REQ-COM-012 서비스 리뷰 평점 읽기 경계다. */
public interface ServiceReviewRatingReader {
    ReviewRatingSummary readServiceRating(long userSn);
}
