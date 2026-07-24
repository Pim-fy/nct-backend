package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.dto.AuctionBidCreateCommand;
import nct.auction.dto.AuctionBidRequest;
import nct.auction.dto.AuctionBidTarget;
import nct.auction.dto.AuctionBuyNowRequest;
import nct.auction.dto.AuctionDetailResponse;
import nct.auction.dto.AuctionRealtimeEvent;
import nct.auction.mapper.AuctionMapper;
import nct.auction.service.AuctionEventPublisher;
import nct.auction.service.AuctionService;
import nct.common.domain.RefType;
import nct.favorite.mapper.ProductFavoriteMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.point.domain.AuctionPolicy;
import nct.point.exception.PointException;
import nct.point.service.PointService;
import nct.product.service.ProductService;
import nct.trade.domain.AuctionTradeSource;
import nct.trade.dto.AuctionTradeCreateCommand;
import nct.trade.service.TradeService;

@ExtendWith(MockitoExtension.class)
class AuctionServicePolicyTest {

    @Mock
    private AuctionMapper auctionMapper;

    @Mock
    private ProductFavoriteMapper productFavoriteMapper;

    @Mock
    private PointService pointService;

    @Mock
    private ObjectProvider<ProductService> productServiceProvider;

    @Mock
    private TradeService tradeService;

    @Mock
    private AuctionEventPublisher auctionEventPublisher;

    @InjectMocks
    private AuctionService auctionService;

    private AuctionBidTarget target;

    @BeforeEach
    void setUp() {
        target = new AuctionBidTarget();
        target.setAuctionId(10L);
        target.setProductId(20L);
        target.setSellerId(30L);
        target.setCurrentPrice(BigDecimal.valueOf(10000));
        target.setBidUnitPrice(BigDecimal.valueOf(1000));
        target.setTradeMethodCode("TRDC0010");
        target.setAuctionStatusCode(AuctionStatusCode.ACTIVE);
        target.setEndDateTime(LocalDateTime.now().plusMinutes(2));
        target.setDatabaseNow(LocalDateTime.now());
        when(auctionMapper.findAuctionBidTargetForUpdate(10L)).thenReturn(target);
    }

    @Test
    void placeBidRejectsAmountBelowPolicyMinimumBidUnit() {
        when(pointService.getAuctionPolicy()).thenReturn(auctionPolicy(3, 2, 3000));
        AuctionBidRequest request = bidRequest(12000);

        assertThatThrownBy(() -> auctionService.placeBid(10L, 40L, request))
                .isInstanceOf(CustomException.class);

        verify(auctionMapper, never()).updateAuctionCurrentPrice(any(), any(), any());
    }

    @Test
    void placeBidPassesPolicyExtensionValuesToMapper() {
        when(pointService.getAuctionPolicy()).thenReturn(auctionPolicy(3, 2, 1000));
        when(auctionMapper.updateAuctionCurrentPrice(10L, BigDecimal.valueOf(12000), "40")).thenReturn(1);
        when(auctionMapper.insertBid(any(AuctionBidCreateCommand.class))).thenAnswer(invocation -> {
            AuctionBidCreateCommand command = invocation.getArgument(0);
            ReflectionTestUtils.setField(command, "bidId", 50L);
            return 1;
        });

        AuctionDetailResponse detail = new AuctionDetailResponse();
        detail.setProductId(20L);
        when(auctionMapper.findAuctionDetail(10L, 40L)).thenReturn(detail);
        when(auctionMapper.findAuctionImages(20L)).thenReturn(List.of());
        when(auctionMapper.findAuctionBids(10L)).thenReturn(List.of());

        when(productFavoriteMapper.existsActive(20L, 40L)).thenReturn(true);

        AuctionDetailResponse response = auctionService.placeBid(10L, 40L, bidRequest(12000));

        verify(auctionMapper).extendAuctionTime(10L, 3, 2, "40");
        verifyRealtimeEvent("BID_PLACED");
        assertThat(response.isFavorite()).isTrue();
    }

    @Test
    void placeBidPropagatesPreviousHighestBidHoldReleaseFailure() {
        target.setCurrentHighestBidderId(35L);
        target.setCurrentHighestBidId(45L);
        when(pointService.getAuctionPolicy()).thenReturn(auctionPolicy(3, 2, 1000));
        when(auctionMapper.updateAuctionCurrentPrice(10L, BigDecimal.valueOf(12000), "40")).thenReturn(1);
        when(auctionMapper.insertBid(any(AuctionBidCreateCommand.class))).thenAnswer(invocation -> {
            AuctionBidCreateCommand command = invocation.getArgument(0);
            ReflectionTestUtils.setField(command, "bidId", 50L);
            return 1;
        });
        doThrow(new PointException(ErrorCode.POINT_HOLD_NOT_FOUND, "기존 홀딩이 없습니다."))
                .when(pointService)
                .releaseHold(35L, RefType.BID, 45L, "상위 입찰 발생에 따른 기존 입찰 홀딩 반환");

        assertThatThrownBy(() -> auctionService.placeBid(10L, 40L, bidRequest(12000)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_HOLD_NOT_FOUND);

        verify(auctionMapper, never()).extendAuctionTime(any(), any(Integer.class), any(Integer.class), any());
    }

    @Test
    void buyNowCreatesAuctionTradeWithoutCreatingChatRoom() {
        target.setInstantBuyPrice(BigDecimal.valueOf(30000));
        when(auctionMapper.insertBid(any(AuctionBidCreateCommand.class))).thenAnswer(invocation -> {
            AuctionBidCreateCommand command = invocation.getArgument(0);
            ReflectionTestUtils.setField(command, "bidId", 50L);
            return 1;
        });
        when(auctionMapper.closeAuctionByInstantBuy(10L, BigDecimal.valueOf(30000), "40")).thenReturn(1);
        stubAuctionDetail();

        auctionService.buyNow(10L, 40L, new AuctionBuyNowRequest());

        ArgumentCaptor<AuctionTradeCreateCommand> commandCaptor =
                ArgumentCaptor.forClass(AuctionTradeCreateCommand.class);
        verify(tradeService).createAuctionTrade(commandCaptor.capture());
        AuctionTradeCreateCommand command = commandCaptor.getValue();
        assertThat(command.getAuctionId()).isEqualTo(10L);
        assertThat(command.getProductId()).isEqualTo(20L);
        assertThat(command.getWinningBidId()).isEqualTo(50L);
        assertThat(command.getSellerUserId()).isEqualTo(30L);
        assertThat(command.getBuyerUserId()).isEqualTo(40L);
        assertThat(command.getTradeAmount()).isEqualByComparingTo("30000");
        assertThat(command.getSource()).isEqualTo(AuctionTradeSource.BUY_NOW);
        verifyRealtimeEvent("BUY_NOW");
    }

    @Test
    void buyNowPropagatesTradeCreationFailure() {
        target.setInstantBuyPrice(BigDecimal.valueOf(30000));
        when(auctionMapper.insertBid(any(AuctionBidCreateCommand.class))).thenAnswer(invocation -> {
            AuctionBidCreateCommand command = invocation.getArgument(0);
            ReflectionTestUtils.setField(command, "bidId", 50L);
            return 1;
        });
        when(auctionMapper.closeAuctionByInstantBuy(10L, BigDecimal.valueOf(30000), "40")).thenReturn(1);
        when(tradeService.createAuctionTrade(any(AuctionTradeCreateCommand.class)))
                .thenThrow(new CustomException(ErrorCode.INVALID_INPUT_VALUE));

        assertThatThrownBy(() -> auctionService.buyNow(10L, 40L, new AuctionBuyNowRequest()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(auctionMapper, never()).findAuctionDetail(10L, 40L);
    }

    @Test
    void finalizeExpiredAuctionCreatesWinningTradeWithoutCreatingChatRoom() {
        target.setEndDateTime(LocalDateTime.now().minusMinutes(1));
        target.setCurrentHighestBidId(50L);
        target.setCurrentHighestBidderId(40L);
        when(auctionMapper.updateExpiredAuctionStatus(10L, AuctionStatusCode.ENDED, "SYSTEM"))
                .thenReturn(1);

        assertThat(auctionService.finalizeExpiredAuction(10L)).isTrue();

        ArgumentCaptor<AuctionTradeCreateCommand> commandCaptor =
                ArgumentCaptor.forClass(AuctionTradeCreateCommand.class);
        verify(tradeService).createAuctionTrade(commandCaptor.capture());
        AuctionTradeCreateCommand command = commandCaptor.getValue();
        assertThat(command.getAuctionId()).isEqualTo(10L);
        assertThat(command.getProductId()).isEqualTo(20L);
        assertThat(command.getWinningBidId()).isEqualTo(50L);
        assertThat(command.getSellerUserId()).isEqualTo(30L);
        assertThat(command.getBuyerUserId()).isEqualTo(40L);
        assertThat(command.getTradeAmount()).isEqualByComparingTo("10000");
        assertThat(command.getSource()).isEqualTo(AuctionTradeSource.AUCTION_WIN);
        verifyRealtimeEvent("AUCTION_FINALIZED");
    }

    @Test
    void finalizeExpiredAuctionWithoutBidDoesNotCreateTrade() {
        target.setEndDateTime(LocalDateTime.now().minusMinutes(1));
        when(auctionMapper.updateExpiredAuctionStatus(10L, AuctionStatusCode.FAILED, "SYSTEM"))
                .thenReturn(1);

        assertThat(auctionService.finalizeExpiredAuction(10L)).isTrue();

        verify(tradeService, never()).createAuctionTrade(any(AuctionTradeCreateCommand.class));
        verifyRealtimeEvent("AUCTION_FINALIZED");
    }

    private void verifyRealtimeEvent(String eventType) {
        ArgumentCaptor<AuctionRealtimeEvent> eventCaptor =
                ArgumentCaptor.forClass(AuctionRealtimeEvent.class);
        verify(auctionEventPublisher).publishAfterCommit(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getAuctionId()).isEqualTo(10L);
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(eventType);
    }

    private void stubAuctionDetail() {
        AuctionDetailResponse detail = new AuctionDetailResponse();
        detail.setProductId(20L);
        when(auctionMapper.findAuctionDetail(10L, 40L)).thenReturn(detail);
        when(auctionMapper.findAuctionImages(20L)).thenReturn(List.of());
        when(auctionMapper.findAuctionBids(10L)).thenReturn(List.of());
    }

    private AuctionBidRequest bidRequest(long bidAmount) {
        AuctionBidRequest request = new AuctionBidRequest();
        request.setBidAmount(BigDecimal.valueOf(bidAmount));
        return request;
    }

    private AuctionPolicy auctionPolicy(int extensionMinutes, int maxExtensionCount, long minBidUnit) {
        AuctionPolicy policy = new AuctionPolicy();
        policy.setAucExtMin(extensionMinutes);
        policy.setAucExtMaxCnt(maxExtensionCount);
        policy.setMinBidUnit(minBidUnit);
        return policy;
    }
}
