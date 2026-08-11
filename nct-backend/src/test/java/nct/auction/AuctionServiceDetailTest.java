package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import nct.auction.dto.AuctionDetailResponse;
import nct.auction.constant.AuctionStatusCode;
import nct.auction.mapper.AuctionMapper;
import nct.auction.service.AuctionEventPublisher;
import nct.auction.service.AuctionService;
import nct.favorite.mapper.ProductFavoriteMapper;
import nct.point.service.PointService;
import nct.product.service.ProductService;
import nct.product.dto.ProductCommentResponse;
import nct.review.dto.TrustScoreResponse;
import nct.review.service.ReviewService;
import nct.trade.dto.AuctionBidTradeReference;
import nct.trade.port.AuctionBidTradeReader;
import nct.trade.service.TradeService;

@ExtendWith(MockitoExtension.class)
class AuctionServiceDetailTest {

    @Mock
    private AuctionMapper auctionMapper;

    @Mock
    private ProductFavoriteMapper productFavoriteMapper;

    @Mock
    private PointService pointService;

    @Mock
    private ObjectProvider<ProductService> productServiceProvider;

    @Mock
    private ProductService productService;

    @Mock
    private TradeService tradeService;

    @Mock
    private AuctionEventPublisher auctionEventPublisher;

    @Mock
    private ReviewService reviewService;

    @Mock
    private AuctionBidTradeReader auctionBidTradeReader;

    @InjectMocks
    private AuctionService auctionService;

    @Test
    void findAuctionDetailDoesNotIncreaseProductViewCount() {
        AuctionDetailResponse detail = new AuctionDetailResponse();
        detail.setProductId(20L);

        when(auctionMapper.findProductIdByAuctionId(10L)).thenReturn(20L);
        when(productServiceProvider.getObject()).thenReturn(productService);
        when(auctionMapper.findAuctionDetail(10L, null)).thenReturn(detail);
        when(auctionMapper.findAuctionImages(20L)).thenReturn(List.of());
        when(auctionMapper.findAuctionBids(10L)).thenReturn(List.of());

        AuctionDetailResponse response = auctionService.findAuctionDetail(10L);

        assertThat(response).isSameAs(detail);
        verify(productService).getProduct(20L);
        verify(productService, never()).increaseViewCount(anyLong(), any(), any());
    }

    @Test
    void findAuctionDetailKeepsMapperHighestBidderResultForCurrentUser() {
        AuctionDetailResponse detail = new AuctionDetailResponse();
        detail.setProductId(20L);
        detail.setCurrentHighestBidder(true);
        detail.setHasBidHistory(true);

        when(auctionMapper.findProductIdByAuctionId(10L)).thenReturn(20L);
        when(productServiceProvider.getObject()).thenReturn(productService);
        when(auctionMapper.findAuctionDetail(10L, 30L)).thenReturn(detail);
        when(auctionMapper.findAuctionImages(20L)).thenReturn(List.of());
        when(auctionMapper.findAuctionBids(10L)).thenReturn(List.of());

        AuctionDetailResponse response = auctionService.findAuctionDetail(10L, 30L);

        assertThat(response.isCurrentHighestBidder()).isTrue();
        assertThat(response.isHasBidHistory()).isTrue();
        verify(auctionMapper).findAuctionDetail(10L, 30L);
    }

    @Test
    void findEndedAuctionDetailIncludesWinnerTradeId() {
        AuctionDetailResponse detail = new AuctionDetailResponse();
        detail.setProductId(20L);
        detail.setAuctionStatusCode(AuctionStatusCode.ENDED);
        detail.setCurrentHighestBidder(true);
        detail.setCurrentHighestBidId(101L);
        AuctionBidTradeReference tradeReference = new AuctionBidTradeReference();
        tradeReference.setBidSn(101L);
        tradeReference.setTradeId(202L);

        when(auctionMapper.findProductIdByAuctionId(10L)).thenReturn(20L);
        when(productServiceProvider.getObject()).thenReturn(productService);
        when(auctionMapper.findAuctionDetail(10L, 30L)).thenReturn(detail);
        when(auctionMapper.findAuctionImages(20L)).thenReturn(List.of());
        when(auctionMapper.findAuctionBids(10L)).thenReturn(List.of());
        when(auctionBidTradeReader.findByBuyerAndBidSns(30L, List.of(101L)))
                .thenReturn(List.of(tradeReference));

        AuctionDetailResponse response = auctionService.findAuctionDetail(10L, 30L, false);

        assertThat(response.getTradeId()).isEqualTo(202L);
        verify(auctionBidTradeReader).findByBuyerAndBidSns(30L, List.of(101L));
    }

    @Test
    void findAuctionDetailIncludesSellerReviewSummary() {
        AuctionDetailResponse detail = new AuctionDetailResponse();
        detail.setProductId(20L);
        detail.setSellerId(30L);

        when(auctionMapper.findProductIdByAuctionId(10L)).thenReturn(20L);
        when(productServiceProvider.getObject()).thenReturn(productService);
        when(auctionMapper.findAuctionDetail(10L, null)).thenReturn(detail);
        when(auctionMapper.findAuctionImages(20L)).thenReturn(List.of());
        when(auctionMapper.findAuctionBids(10L)).thenReturn(List.of());
        when(reviewService.getTrustScore(30L)).thenReturn(TrustScoreResponse.builder()
                .usrSn(30L)
                .totalScore(4.2)
                .totalCount(12)
                .hasReviews(true)
                .build());

        AuctionDetailResponse response = auctionService.findAuctionDetail(10L);

        assertThat(response.getSellerRating()).isEqualTo(4.2);
        assertThat(response.getSellerReviewCount()).isEqualTo(12);
        verify(reviewService).getTrustScore(30L);
    }

    @Test
    void findAuctionDetailIncludesProductUpdateHistory() {
        AuctionDetailResponse detail = new AuctionDetailResponse();
        detail.setProductId(20L);
        ProductCommentResponse comment = org.mockito.Mockito.mock(ProductCommentResponse.class);
        LocalDateTime registeredAt = LocalDateTime.of(2026, 7, 27, 14, 30);

        when(auctionMapper.findProductIdByAuctionId(10L)).thenReturn(20L);
        when(productServiceProvider.getObject()).thenReturn(productService);
        when(productServiceProvider.getIfAvailable()).thenReturn(productService);
        when(auctionMapper.findAuctionDetail(10L, null)).thenReturn(detail);
        when(auctionMapper.findAuctionImages(20L)).thenReturn(List.of());
        when(auctionMapper.findAuctionBids(10L)).thenReturn(List.of());
        when(comment.getPrdCmtSn()).thenReturn(101L);
        when(comment.getPrdCmtTtl()).thenReturn("상품 상태 안내");
        when(comment.getPrdCmtCn()).thenReturn("외관 상태 설명을 보완했습니다.");
        when(comment.getPrdCmtRegDt()).thenReturn(registeredAt);
        when(productService.getComments(20L)).thenReturn(List.of(comment));

        AuctionDetailResponse response = auctionService.findAuctionDetail(10L);

        assertThat(response.getProductUpdates()).hasSize(1);
        assertThat(response.getProductUpdates().get(0).getUpdateId()).isEqualTo(101L);
        assertThat(response.getProductUpdates().get(0).getTitle()).isEqualTo("상품 상태 안내");
        assertThat(response.getProductUpdates().get(0).getContent())
                .isEqualTo("외관 상태 설명을 보완했습니다.");
        assertThat(response.getProductUpdates().get(0).getRegisteredAt()).isEqualTo(registeredAt);
        verify(productService).getComments(20L);
    }

    @Test
    void findAuctionDetailSkipsSupplementalDataWhenDisabled() {
        AuctionDetailResponse detail = new AuctionDetailResponse();
        detail.setProductId(20L);
        detail.setSellerId(30L);

        when(auctionMapper.findProductIdByAuctionId(10L)).thenReturn(20L);
        when(productServiceProvider.getObject()).thenReturn(productService);
        when(auctionMapper.findAuctionDetail(10L, null)).thenReturn(detail);
        when(auctionMapper.findAuctionImages(20L)).thenReturn(List.of());
        when(auctionMapper.findAuctionBids(10L)).thenReturn(List.of());

        AuctionDetailResponse response = auctionService.findAuctionDetail(10L, null, false);

        assertThat(response.getSellerRating()).isNull();
        assertThat(response.getSellerReviewCount()).isNull();
        assertThat(response.getProductUpdates()).isEmpty();
        verify(reviewService, never()).getTrustScore(anyLong());
        verify(productService, never()).getComments(anyLong());
    }
}
