package nct.provider.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.provider.dto.ProviderProfileRequest;
import nct.provider.dto.ProviderProfileResponse;
import nct.provider.mapper.ProviderProfileMapper;
import nct.review.dto.ReviewRatingSummary;
import nct.review.port.ServiceReviewRatingReader;

/** 담당자 7 · F-PROV-004/F-COM-009: 제공자 프로필과 서비스 리뷰 평점 캐시를 관리한다. */
@Service
@RequiredArgsConstructor
public class ProviderProfileService {
    private final ProviderProfileMapper mapper;
    private final ActiveProviderGuard activeProviderGuard;
    private final ServiceReviewRatingReader serviceReviewRatingReader;

    @Transactional(readOnly = true)
    public ProviderProfileResponse getMine(Long userSn) {
        requireActiveProvider(userSn);
        ProviderProfileResponse profile = mapper.findActiveByUserSn(userSn)
                .orElseGet(() -> emptyProfile(userSn));

        // 담당자 7 F-PROV-013: 프로필을 아직 작성하지 않은 제공자도 승인된 분야 권한은 표시한다.
        if (profile.getCategories() == null || profile.getCategories().isEmpty()) {
            profile.setCategories(mapper.findActiveCategoryNames(userSn));
        }
        applyLiveServiceRating(profile, userSn);
        return profile;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProviderProfileResponse updateMine(Long userSn, ProviderProfileRequest request) {
        requireActiveProvider(userSn);
        if (request == null) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        mapper.upsert(userSn, trimToNull(request.getIntroduction()), trimToNull(request.getAvailableArea()),
                request.getProfileFileSn(), String.valueOf(userSn));
        ReviewRatingSummary rating = serviceReviewRatingReader.readServiceRating(userSn);
        mapper.updateReviewRating(
                userSn,
                rating.getAverageScore(),
                rating.getReviewCount(),
                String.valueOf(userSn));
        return mapper.findActiveByUserSn(userSn).orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Transactional(readOnly = true)
    public ProviderProfileResponse getPublic(Long providerUserSn) {
        requireActiveProvider(providerUserSn);
        ProviderProfileResponse profile = mapper.findActiveByUserSn(providerUserSn)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        applyLiveServiceRating(profile, providerUserSn);
        return profile;
    }

    // 활성 제공자 검사는 포트폴리오 서비스와 공용 가드(ActiveProviderGuard)로 통합 (2026-08-05 중복 정리)
    private void requireActiveProvider(Long userSn) {
        activeProviderGuard.requireActive(userSn);
    }

    private ProviderProfileResponse emptyProfile(Long userSn) {
        return ProviderProfileResponse.builder().userSn(userSn).reviewAverageScore(BigDecimal.ZERO).reviewCount(0L).build();
    }

    /** 담당자 7 · REQ-COM-012: 공개·본인 프로필 모두 서비스 리뷰 실시간 집계를 같은 기준으로 표시한다. */
    private void applyLiveServiceRating(ProviderProfileResponse profile, Long userSn) {
        ReviewRatingSummary rating = serviceReviewRatingReader.readServiceRating(userSn);
        profile.setReviewAverageScore(rating.getAverageScore());
        profile.setReviewCount(rating.getReviewCount());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
