package nct.provider.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.provider.dto.ProviderProfileRequest;
import nct.provider.dto.ProviderProfileResponse;
import nct.provider.mapper.ProviderProfileMapper;
import nct.review.dto.ServiceReviewRatingSummary;
import nct.review.port.ServiceReviewRatingReader;

/** 담당자 7 · F-PROV-004/F-COM-009: 프로필 변경 전 제공자 검증과 리뷰 평균 동기화 회귀 테스트다.
 *  활성 제공자 판정 자체(회원 상태·권한·제재)는 ActiveProviderGuard로 통합돼 그쪽 테스트가
 *  지키고, 여기서는 가드 호출 여부와 가드 실패 시 차단만 확인한다 (2026-08-05 중복 정리). */
@ExtendWith(MockitoExtension.class)
class ProviderProfileServiceTest {
    @Mock private ProviderProfileMapper mapper;
    @Mock private ActiveProviderGuard activeProviderGuard;
    @Mock private ServiceReviewRatingReader serviceReviewRatingReader;
    @InjectMocks private ProviderProfileService service;

    @Test
    void updateMineChecksActiveProviderAndReturnsSavedProfile() {
        ProviderProfileRequest request = new ProviderProfileRequest();
        request.setIntroduction("소개");
        request.setAvailableArea("서울");
        request.setProfileFileSn(55L);
        ProviderProfileResponse saved = profile(101L);
        ServiceReviewRatingSummary rating = new ServiceReviewRatingSummary(new BigDecimal("4.5"), 2L);
        when(serviceReviewRatingReader.read(101L)).thenReturn(rating);
        when(mapper.findActiveByUserSn(101L)).thenReturn(Optional.of(saved));

        ProviderProfileResponse result = service.updateMine(101L, request);

        assertThat(result.getUserSn()).isEqualTo(101L);
        verify(activeProviderGuard).requireActive(101L);
        verify(mapper).upsert(101L, "소개", "서울", 55L, "101");
        verify(mapper).updateReviewRating(101L, new BigDecimal("4.5"), 2L, "101");
        InOrder cacheRefreshOrder = inOrder(mapper, serviceReviewRatingReader);
        cacheRefreshOrder.verify(mapper).upsert(101L, "소개", "서울", 55L, "101");
        cacheRefreshOrder.verify(serviceReviewRatingReader).read(101L);
        cacheRefreshOrder.verify(mapper).updateReviewRating(101L, new BigDecimal("4.5"), 2L, "101");
        cacheRefreshOrder.verify(mapper).findActiveByUserSn(101L);
    }

    @Test
    void suspendedProviderCannotReadPublicProfile() {
        // 제재 판정은 가드가 담당(ActiveProviderGuardTest) — 여기선 가드 실패가 조회를 막는 것만 확인
        doThrow(new CustomException(ErrorCode.FORBIDDEN)).when(activeProviderGuard).requireActive(101L);

        assertThatThrownBy(() -> service.getPublic(101L))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void withdrawnProviderCannotExposePublicProfile() {
        doThrow(new CustomException(ErrorCode.NOT_FOUND)).when(activeProviderGuard).requireActive(101L);

        assertThatThrownBy(() -> service.getPublic(101L))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private ProviderProfileResponse profile(Long userSn) {
        return ProviderProfileResponse.builder().userSn(userSn).introduction("소개").availableArea("서울")
                .reviewAverageScore(BigDecimal.ZERO).reviewCount(0L).build();
    }
}
