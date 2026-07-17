package nct.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import nct.common.file.FileStorageService;
import nct.review.constant.ReviewDomainCode;
import nct.review.constant.ReviewFileRefCode;
import nct.review.domain.Review;
import nct.review.dto.MyReviewItem;
import nct.review.dto.ReviewCreateResult;
import nct.review.dto.WritableTradeItem;
import nct.review.exception.InvalidRatingException;
import nct.review.exception.TradeNotReviewableException;
import nct.review.mapper.ReviewMapper;

/**
 * [ReviewService 단위 테스트]
 * - ReviewMapper 의 TRADE 조회는 실제 DB 없이 Mockito 로 흉내낸다
 *   (TRADE 임시 조회의 정확한 SQL 자체가 아니라, 서비스 계층의 검증·조합 로직을 검증하는 테스트다).
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewMapper reviewMapper;
    @Mock private FileStorageService fileStorageService;

    private ReviewService reviewService;

    private static final long USR_SN = 1L;
    private static final long TRADE_ID = 100L;
    private static final long COUNTERPART_USR_SN = 2L;

    private void setUp() {
        reviewService = new ReviewService(reviewMapper, fileStorageService);
    }

    private WritableTradeItem healthyTrade(String dealType) {
        return WritableTradeItem.builder()
                .id(TRADE_ID)
                .title("테스트 상품")
                .dealType(dealType)
                .partyLabel("판매자")
                .partyName("이**")
                .completedDate("2026-06-18")
                .counterpartUsrSn(COUNTERPART_USR_SN)
                .build();
    }

    @Test
    void 평점이_범위를_벗어나면_거래_조회_없이_바로_실패한다() {
        setUp();

        assertThatThrownBy(() -> reviewService.createReview(USR_SN, TRADE_ID, 0, "내용", null))
                .isInstanceOf(InvalidRatingException.class);
        assertThatThrownBy(() -> reviewService.createReview(USR_SN, TRADE_ID, 6, "내용", null))
                .isInstanceOf(InvalidRatingException.class);

        // 평점 검증에서 이미 막혔으므로 거래 조회 자체가 일어나지 않아야 한다.
        verify(reviewMapper, never()).selectWritableTrade(any(Long.class), any(Long.class));
    }

    @Test
    void 작성_가능한_거래가_아니면_예외가_발생하고_리뷰가_저장되지_않는다() {
        setUp();
        when(reviewMapper.selectWritableTrade(TRADE_ID, USR_SN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.createReview(USR_SN, TRADE_ID, 5, "내용", null))
                .isInstanceOf(TradeNotReviewableException.class);

        verify(reviewMapper, never()).insertReview(any());
    }

    @Test
    void 물건거래_리뷰는_RVWC0001_도메인코드로_저장된다() {
        setUp();
        when(reviewMapper.selectWritableTrade(TRADE_ID, USR_SN)).thenReturn(Optional.of(healthyTrade("goods")));
        doAnswer(invocation -> {
            Review saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "rvwSn", 900L);
            return null;
        }).when(reviewMapper).insertReview(any(Review.class));

        ReviewCreateResult result = reviewService.createReview(USR_SN, TRADE_ID, 5, "만족합니다", null);

        // then: 저장된 Review 의 필드가 정확한지 확인
        org.mockito.ArgumentCaptor<Review> captor = org.mockito.ArgumentCaptor.forClass(Review.class);
        verify(reviewMapper).insertReview(captor.capture());
        Review saved = captor.getValue();
        assertThat(saved.getTrdSn()).isEqualTo(TRADE_ID);
        assertThat(saved.getRevwrUsrSn()).isEqualTo(USR_SN);
        assertThat(saved.getRevwdUsrSn()).isEqualTo(COUNTERPART_USR_SN);
        assertThat(saved.getRvwDomainCd()).isEqualTo(ReviewDomainCode.GOODS);
        assertThat(saved.getRvwScore()).isEqualTo(5);

        assertThat(result.getId()).isEqualTo(900L);
        assertThat(result.getPhotoCount()).isZero();

        // 사진이 없으므로 첨부 로직은 호출되지 않아야 한다.
        verify(fileStorageService, never()).attach(any(), any(), any(Long.class), any(Long.class));
    }

    @Test
    void 서비스거래_리뷰는_RVWC0002_도메인코드로_저장된다() {
        setUp();
        when(reviewMapper.selectWritableTrade(TRADE_ID, USR_SN)).thenReturn(Optional.of(healthyTrade("service")));

        reviewService.createReview(USR_SN, TRADE_ID, 4, "좋아요", null);

        org.mockito.ArgumentCaptor<Review> captor = org.mockito.ArgumentCaptor.forClass(Review.class);
        verify(reviewMapper).insertReview(captor.capture());
        assertThat(captor.getValue().getRvwDomainCd()).isEqualTo(ReviewDomainCode.SERVICE);
    }

    @Test
    void 사진이_있으면_저장된_리뷰번호로_첨부를_요청한다() {
        setUp();
        when(reviewMapper.selectWritableTrade(TRADE_ID, USR_SN)).thenReturn(Optional.of(healthyTrade("goods")));
        doAnswer(invocation -> {
            Review saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "rvwSn", 901L);
            return null;
        }).when(reviewMapper).insertReview(any(Review.class));

        MultipartFile photo1 = new MockMultipartFile("photos", "a.jpg", "image/jpeg", "data".getBytes());
        MultipartFile photo2 = new MockMultipartFile("photos", "b.jpg", "image/jpeg", "data".getBytes());

        ReviewCreateResult result = reviewService.createReview(
                USR_SN, TRADE_ID, 5, "사진 첨부 테스트", List.of(photo1, photo2));

        assertThat(result.getPhotoCount()).isEqualTo(2);
        verify(fileStorageService).attach(List.of(photo1, photo2), ReviewFileRefCode.REVIEW, 901L, USR_SN);
    }

    @Test
    void 내가_작성한_리뷰_목록은_각_리뷰마다_사진_URL을_채워서_반환한다() {
        setUp();
        MyReviewItem item = MyReviewItem.builder()
                .id(1L).tradeId(TRADE_ID).rating(5).content("좋았어요")
                .title("상품").dealType("goods").partyLabel("판매자").partyName("이**")
                .completedDate("2026-06-18")
                .build();
        when(reviewMapper.selectMyReviews(USR_SN)).thenReturn(List.of(item));
        when(fileStorageService.getUrls(ReviewFileRefCode.REVIEW, 1L))
                .thenReturn(List.of("/uploads/2026/06/18/a.jpg"));

        List<MyReviewItem> result = reviewService.getMyReviews(USR_SN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPhotos()).containsExactly("/uploads/2026/06/18/a.jpg");
        // 원본 필드는 그대로 유지되어야 한다 (toBuilder로 photos만 채워 넣었는지 확인)
        assertThat(result.get(0).getTitle()).isEqualTo("상품");
    }
}
