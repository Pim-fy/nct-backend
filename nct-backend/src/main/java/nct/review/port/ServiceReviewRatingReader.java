package nct.review.port;

import nct.review.dto.ServiceReviewRatingSummary;

/** 담당자 3 제공·담당자 7 소비 · F-COM-009: 서비스 리뷰 평균을 읽는 도메인 경계다. */
public interface ServiceReviewRatingReader {
    ServiceReviewRatingSummary read(long userSn);
}
