package nct.provider.port;

import java.math.BigDecimal;

/** 담당자 7 · F-COM-009: REVIEW가 PROVIDER_PROFILE을 직접 쓰지 않도록 제공하는 갱신 계약이다. */
public interface ProviderReviewRatingPort {
    boolean lockReviewRating(long userSn);

    void updateReviewRating(long userSn, BigDecimal averageScore, long reviewCount);
}
