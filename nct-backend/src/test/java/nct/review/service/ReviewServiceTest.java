package nct.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

import nct.file.domain.FileMeta;
import nct.file.service.FileStorageService;
import nct.review.constant.ReviewDomainCode;
import nct.review.domain.Review;
import nct.review.domain.ReviewImage;
import nct.review.dto.MyReviewItem;
import nct.review.dto.ReviewCreateResult;
import nct.review.dto.ReviewUpdateResult;
import nct.review.dto.WritableTradeItem;
import nct.review.exception.InvalidRatingException;
import nct.review.exception.ReviewNotFoundException;
import nct.review.exception.TooManyReviewPhotosException;
import nct.review.exception.TradeNotReviewableException;
import nct.review.mapper.ReviewImageMapper;
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
    @Mock private ReviewImageMapper reviewImageMapper;

    private ReviewService reviewService;

    private static final long USR_SN = 1L;
    private static final long TRADE_ID = 100L;
    private static final long COUNTERPART_USR_SN = 2L;

    private void setUp() {
        reviewService = new ReviewService(reviewMapper, fileStorageService, reviewImageMapper);
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

        verify(fileStorageService, never()).storeImage(any(), any(), any());
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
    void 사진이_있으면_저장된_리뷰번호로_REVIEW_IMAGE에_연결한다() {
        setUp();
        when(reviewMapper.selectWritableTrade(TRADE_ID, USR_SN)).thenReturn(Optional.of(healthyTrade("goods")));
        doAnswer(invocation -> {
            Review saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "rvwSn", 901L);
            return null;
        }).when(reviewMapper).insertReview(any(Review.class));
        when(fileStorageService.storeImage(any(), eq("review"), eq(USR_SN)))
                .thenReturn(FileMeta.builder().flSn(10L).build())
                .thenReturn(FileMeta.builder().flSn(11L).build());

        MultipartFile photo1 = new MockMultipartFile("photos", "a.jpg", "image/jpeg", "data".getBytes());
        MultipartFile photo2 = new MockMultipartFile("photos", "b.jpg", "image/jpeg", "data".getBytes());

        ReviewCreateResult result = reviewService.createReview(
                USR_SN, TRADE_ID, 5, "사진 첨부 테스트", List.of(photo1, photo2));

        assertThat(result.getPhotoCount()).isEqualTo(2);

        org.mockito.ArgumentCaptor<List<ReviewImage>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(reviewImageMapper).insertAll(captor.capture());
        List<ReviewImage> inserted = captor.getValue();
        assertThat(inserted).hasSize(2);
        assertThat(inserted.get(0).getRvwSn()).isEqualTo(901L);
        assertThat(inserted.get(0).getFlSn()).isEqualTo(10L);
        assertThat(inserted.get(0).getRvwImgSortNo()).isEqualTo(0);
        assertThat(inserted.get(1).getFlSn()).isEqualTo(11L);
        assertThat(inserted.get(1).getRvwImgSortNo()).isEqualTo(1);
    }

    @Test
    void 등록시_사진이_5장을_넘으면_거래_조회_없이_바로_거부된다() {
        setUp();

        assertThatThrownBy(() -> reviewService.createReview(
                USR_SN, TRADE_ID, 5, "내용", mockPhotos(6)))
                .isInstanceOf(TooManyReviewPhotosException.class);

        verify(reviewMapper, never()).selectWritableTrade(any(Long.class), any(Long.class));
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
        when(reviewImageMapper.selectUrlsByReviewSn(1L))
                .thenReturn(List.of("/api/attachment/review/20260618/a.jpg"));

        List<MyReviewItem> result = reviewService.getMyReviews(USR_SN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPhotos()).containsExactly("/api/attachment/review/20260618/a.jpg");
        assertThat(result.get(0).getTitle()).isEqualTo("상품");
    }

    @Test
    void 리뷰_수정시_평점이_범위를_벗어나면_매퍼_호출_없이_바로_실패한다() {
        setUp();

        assertThatThrownBy(() -> reviewService.updateReview(USR_SN, 900L, 0, "내용", null))
                .isInstanceOf(InvalidRatingException.class);
        assertThatThrownBy(() -> reviewService.updateReview(USR_SN, 900L, 6, "내용", null))
                .isInstanceOf(InvalidRatingException.class);

        verify(reviewMapper, never()).updateReview(any(Long.class), any(Long.class), any(Integer.class), any());
    }

    @Test
    void 존재하지_않거나_본인_소유가_아닌_리뷰를_수정하면_예외가_발생한다() {
        setUp();
        when(reviewMapper.updateReview(900L, USR_SN, 4, "수정된 내용")).thenReturn(0);

        assertThatThrownBy(() -> reviewService.updateReview(USR_SN, 900L, 4, "수정된 내용", null))
                .isInstanceOf(ReviewNotFoundException.class);

        verify(fileStorageService, never()).storeImage(any(), any(), any());
    }

    @Test
    void 리뷰_수정이_성공하면_평점과_내용이_반영된다() {
        setUp();
        when(reviewMapper.updateReview(900L, USR_SN, 4, "수정된 내용")).thenReturn(1);

        ReviewUpdateResult result = reviewService.updateReview(USR_SN, 900L, 4, "수정된 내용", null);

        assertThat(result.getId()).isEqualTo(900L);
        assertThat(result.getRating()).isEqualTo(4);
        assertThat(result.getAddedPhotoCount()).isZero();
        verify(fileStorageService, never()).storeImage(any(), any(), any());
    }

    @Test
    void 리뷰_수정시_새_사진이_있으면_REVIEW_IMAGE에_추가로_연결한다() {
        setUp();
        when(reviewMapper.updateReview(900L, USR_SN, 4, "수정된 내용")).thenReturn(1);
        when(fileStorageService.storeImage(any(), eq("review"), eq(USR_SN)))
                .thenReturn(FileMeta.builder().flSn(20L).build());
        MultipartFile photo = new MockMultipartFile("photos", "c.jpg", "image/jpeg", "data".getBytes());

        ReviewUpdateResult result = reviewService.updateReview(USR_SN, 900L, 4, "수정된 내용", List.of(photo));

        assertThat(result.getAddedPhotoCount()).isEqualTo(1);

        org.mockito.ArgumentCaptor<List<ReviewImage>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(reviewImageMapper).insertAll(captor.capture());
        assertThat(captor.getValue().get(0).getRvwSn()).isEqualTo(900L);
        assertThat(captor.getValue().get(0).getFlSn()).isEqualTo(20L);
    }

    @Test
    void 수정시_기존_사진과_새_사진을_합쳐_5장을_넘으면_거부된다() {
        setUp();
        when(reviewImageMapper.selectUrlsByReviewSn(900L)).thenReturn(
                List.of("a.jpg", "b.jpg", "c.jpg", "d.jpg")); // 기존 4장

        assertThatThrownBy(() -> reviewService.updateReview(
                USR_SN, 900L, 4, "수정된 내용", mockPhotos(2))) // 4 + 2 = 6장
                .isInstanceOf(TooManyReviewPhotosException.class);

        verify(reviewMapper, never()).updateReview(any(Long.class), any(Long.class), any(Integer.class), any());
    }

    @Test
    void 수정시_기존_사진과_새_사진을_합쳐_정확히_5장이면_허용된다() {
        setUp();
        when(reviewImageMapper.selectUrlsByReviewSn(900L)).thenReturn(
                List.of("a.jpg", "b.jpg", "c.jpg")); // 기존 3장
        when(reviewMapper.updateReview(900L, USR_SN, 4, "수정된 내용")).thenReturn(1);
        when(fileStorageService.storeImage(any(), eq("review"), eq(USR_SN)))
                .thenReturn(FileMeta.builder().flSn(30L).build())
                .thenReturn(FileMeta.builder().flSn(31L).build());

        ReviewUpdateResult result = reviewService.updateReview(
                USR_SN, 900L, 4, "수정된 내용", mockPhotos(2)); // 3 + 2 = 5장

        assertThat(result.getAddedPhotoCount()).isEqualTo(2);
    }

    /** 개수 검증 테스트 전용 — 내용은 안 쓰고 개수만 필요한 목업 파일 목록 */
    private List<MultipartFile> mockPhotos(int count) {
        List<MultipartFile> photos = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            photos.add(new MockMultipartFile("photos", i + ".jpg", "image/jpeg", "data".getBytes()));
        }
        return photos;
    }

    @Test
    void 리뷰_삭제가_성공하면_매퍼의_소프트_삭제가_호출된다() {
        setUp();
        when(reviewMapper.deleteReview(900L, USR_SN)).thenReturn(1);

        reviewService.deleteReview(USR_SN, 900L);

        verify(reviewMapper).deleteReview(900L, USR_SN);
    }

    @Test
    void 존재하지_않거나_본인_소유가_아닌_리뷰를_삭제하면_예외가_발생한다() {
        setUp();
        when(reviewMapper.deleteReview(900L, USR_SN)).thenReturn(0);

        assertThatThrownBy(() -> reviewService.deleteReview(USR_SN, 900L))
                .isInstanceOf(ReviewNotFoundException.class);
    }
}
