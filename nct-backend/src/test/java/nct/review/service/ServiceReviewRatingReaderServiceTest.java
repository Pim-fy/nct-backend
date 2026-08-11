package nct.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.review.dto.ServiceReviewRatingSummary;
import nct.review.mapper.ReviewMapper;

/** F-COM-009: 제공자 프로필 생성 시 사용하는 리뷰 읽기 포트를 검증한다. */
@ExtendWith(MockitoExtension.class)
class ServiceReviewRatingReaderServiceTest {

    @Mock private ReviewMapper reviewMapper;
    @InjectMocks private ServiceReviewRatingReaderService service;

    @Test
    void readsTheReviewOwnedServiceSummary() {
        ServiceReviewRatingSummary expected = new ServiceReviewRatingSummary(new BigDecimal("4.5"), 2L);
        when(reviewMapper.selectServiceReviewRatingSummary(101L)).thenReturn(expected);

        assertThat(service.read(101L)).isSameAs(expected);
        verify(reviewMapper).selectServiceReviewRatingSummary(101L);
    }
}
