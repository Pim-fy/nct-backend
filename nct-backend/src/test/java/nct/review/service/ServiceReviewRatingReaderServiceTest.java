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

import nct.review.dto.ReviewRatingSummary;
import nct.review.mapper.ReviewMapper;

/** 담당자 7 · F-COM-009/REQ-COM-012: 제공자 프로필용 서비스 평점 읽기 포트를 검증한다. */
@ExtendWith(MockitoExtension.class)
class ServiceReviewRatingReaderServiceTest {

    @Mock private ReviewMapper reviewMapper;
    @InjectMocks private ServiceReviewRatingReaderService service;

    @Test
    void readsOnlyTheServiceReviewSummaryContract() {
        ReviewRatingSummary expected = new ReviewRatingSummary(new BigDecimal("4.5"), 2L);
        when(reviewMapper.selectServiceReviewRatingSummary(101L)).thenReturn(expected);

        assertThat(service.readServiceRating(101L)).isSameAs(expected);
        verify(reviewMapper).selectServiceReviewRatingSummary(101L);
    }
}
