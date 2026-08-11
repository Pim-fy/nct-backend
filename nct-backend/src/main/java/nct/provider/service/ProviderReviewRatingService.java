package nct.provider.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.provider.mapper.ProviderProfileMapper;
import nct.provider.port.ProviderReviewRatingPort;

/** 담당자 7 · F-COM-009: 리뷰 원천 집계값을 제공자 프로필의 검색용 캐시에 반영한다. */
@Service
@RequiredArgsConstructor
public class ProviderReviewRatingService implements ProviderReviewRatingPort {

    private final ProviderProfileMapper mapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockReviewRating(long userSn) {
        return mapper.lockReviewRatingByUserSn(userSn) != null;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateReviewRating(long userSn, BigDecimal averageScore, long reviewCount) {
        mapper.updateReviewRating(userSn, averageScore, reviewCount, "SYSTEM");
    }
}
