package nct.provider.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.provider.mapper.ProviderProfileMapper;

/** 담당자 7 · F-COM-009: 제공자 평점 캐시 갱신 포트의 프로필 부재 처리를 검증한다. */
@ExtendWith(MockitoExtension.class)
class ProviderReviewRatingServiceTest {

    @Mock private ProviderProfileMapper mapper;
    @InjectMocks private ProviderReviewRatingService service;

    @Test
    void profileMissingDoesNotAcquireALock() {
        when(mapper.lockReviewRatingByUserSn(101L)).thenReturn(null);

        assertThat(service.lockReviewRating(101L)).isFalse();

        verify(mapper).lockReviewRatingByUserSn(101L);
    }

    @Test
    void profileMissingIsANormalNoOp() {
        BigDecimal averageScore = new BigDecimal("4.5");
        when(mapper.updateReviewRating(101L, averageScore, 2L, "SYSTEM")).thenReturn(0);

        assertThatCode(() -> service.updateReviewRating(101L, averageScore, 2L))
                .doesNotThrowAnyException();

        verify(mapper).updateReviewRating(101L, averageScore, 2L, "SYSTEM");
    }
}
