package nct.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import nct.auction.service.AuctionService;
import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.PageResponse;
import nct.notification.service.NotificationService;
import nct.provider.port.ProviderReviewRatingPort;
import nct.review.dto.UserReviewItem;
import nct.review.mapper.ReviewImageMapper;
import nct.review.mapper.ReviewMapper;

/** 담당자 7 · F-COM-008: 받은 물품 리뷰 역할 필터의 정규화·검증·조회 전달을 검증한다. */
@ExtendWith(MockitoExtension.class)
class ReviewReceivedRoleServiceTest {

    @Mock private ReviewMapper reviewMapper;
    @Mock private FileStorageService fileStorageService;
    @Mock private ReviewImageMapper reviewImageMapper;
    @Mock private NotificationService notificationService;
    @Mock private ProviderReviewRatingPort providerReviewRatingPort;
    @Mock private ObjectProvider<AuctionService> auctionServiceProvider;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(
                reviewMapper,
                fileStorageService,
                reviewImageMapper,
                notificationService,
                providerReviewRatingPort,
                auctionServiceProvider);
    }

    @Test
    void receivedGoodsReviewsPassTheSameSellerRoleToListAndCountQueries() {
        UserReviewItem item = UserReviewItem.builder()
                .reviewId(901L)
                .reviewerName("김철수")
                .build();
        when(reviewMapper.selectReviewsByReceiver(22L, "goods", "SELLER", 0, 10))
                .thenReturn(List.of(item));
        when(reviewMapper.countReviewsByReceiver(22L, "goods", "SELLER"))
                .thenReturn(1L);

        PageResponse<UserReviewItem> result = reviewService.getReviewsAboutUser(
                22L,
                "goods",
                " seller ",
                0,
                10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getReviewerName()).isEqualTo("김철수");
        assertThat(result.getTotalCount()).isEqualTo(1L);
        verify(reviewMapper).selectReviewsByReceiver(22L, "goods", "SELLER", 0, 10);
        verify(reviewMapper).countReviewsByReceiver(22L, "goods", "SELLER");
    }

    @Test
    void allRoleUsesTheExistingUnfilteredQueryContract() {
        when(reviewMapper.selectReviewsByReceiver(eq(22L), eq("goods"), isNull(), eq(0), eq(10)))
                .thenReturn(List.of());
        when(reviewMapper.countReviewsByReceiver(eq(22L), eq("goods"), isNull()))
                .thenReturn(0L);

        PageResponse<UserReviewItem> result = reviewService.getReviewsAboutUser(
                22L,
                "goods",
                "ALL",
                0,
                10);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalCount()).isZero();
        verify(reviewMapper).selectReviewsByReceiver(eq(22L), eq("goods"), isNull(), eq(0), eq(10));
        verify(reviewMapper).countReviewsByReceiver(eq(22L), eq("goods"), isNull());
    }

    @Test
    void omittedRoleUsesTheExistingUnfilteredQueryContract() {
        when(reviewMapper.selectReviewsByReceiver(eq(22L), eq("goods"), isNull(), eq(0), eq(10)))
                .thenReturn(List.of());
        when(reviewMapper.countReviewsByReceiver(eq(22L), eq("goods"), isNull()))
                .thenReturn(0L);

        reviewService.getReviewsAboutUser(22L, "goods", null, 0, 10);

        verify(reviewMapper).selectReviewsByReceiver(eq(22L), eq("goods"), isNull(), eq(0), eq(10));
        verify(reviewMapper).countReviewsByReceiver(eq(22L), eq("goods"), isNull());
    }

    @Test
    void omittedDealTypeUsesTheExistingAllReviewsQueryContract() {
        when(reviewMapper.selectReviewsByReceiver(eq(22L), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(List.of());
        when(reviewMapper.countReviewsByReceiver(eq(22L), isNull(), isNull()))
                .thenReturn(0L);

        reviewService.getReviewsAboutUser(22L, null, null, 0, 10);

        verify(reviewMapper).selectReviewsByReceiver(eq(22L), isNull(), isNull(), eq(0), eq(10));
        verify(reviewMapper).countReviewsByReceiver(eq(22L), isNull(), isNull());
    }

    @Test
    void serviceDealTypeUsesTheServiceReviewsQueryContract() {
        when(reviewMapper.selectReviewsByReceiver(eq(22L), eq("service"), isNull(), eq(0), eq(10)))
                .thenReturn(List.of());
        when(reviewMapper.countReviewsByReceiver(eq(22L), eq("service"), isNull()))
                .thenReturn(0L);

        reviewService.getReviewsAboutUser(22L, "service", null, 0, 10);

        verify(reviewMapper).selectReviewsByReceiver(eq(22L), eq("service"), isNull(), eq(0), eq(10));
        verify(reviewMapper).countReviewsByReceiver(eq(22L), eq("service"), isNull());
    }

    @Test
    void receivedGoodsReviewsNormalizeAndPassBuyerRole() {
        when(reviewMapper.selectReviewsByReceiver(22L, "goods", "BUYER", 0, 10))
                .thenReturn(List.of());
        when(reviewMapper.countReviewsByReceiver(22L, "goods", "BUYER"))
                .thenReturn(0L);

        reviewService.getReviewsAboutUser(22L, "goods", " buyer ", 0, 10);

        verify(reviewMapper).selectReviewsByReceiver(22L, "goods", "BUYER", 0, 10);
        verify(reviewMapper).countReviewsByReceiver(22L, "goods", "BUYER");
    }

    @Test
    void serviceReviewsRejectSellerOrBuyerRoleBeforeQuerying() {
        assertThatThrownBy(() -> reviewService.getReviewsAboutUser(
                22L,
                "service",
                "BUYER",
                0,
                10))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verifyNoReviewListQuery();
    }

    @Test
    void unknownRoleIsRejectedBeforeQuerying() {
        assertThatThrownBy(() -> reviewService.getReviewsAboutUser(
                22L,
                "goods",
                "PROVIDER",
                0,
                10))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verifyNoReviewListQuery();
    }

    @Test
    void unknownDealTypeIsRejectedBeforeQuerying() {
        assertThatThrownBy(() -> reviewService.getReviewsAboutUser(
                22L,
                "invalid",
                null,
                0,
                10))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verifyNoReviewListQuery();
    }

    @Test
    void blankDealTypeIsRejectedBeforeQuerying() {
        assertThatThrownBy(() -> reviewService.getReviewsAboutUser(
                22L,
                " ",
                null,
                0,
                10))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verifyNoReviewListQuery();
    }

    private void verifyNoReviewListQuery() {
        verify(reviewMapper, never()).selectReviewsByReceiver(
                anyLong(),
                any(),
                any(),
                anyInt(),
                anyInt());
        verify(reviewMapper, never()).countReviewsByReceiver(anyLong(), any(), any());
    }
}
