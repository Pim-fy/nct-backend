package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

import nct.agree.domain.AgreeActType;
import nct.agree.domain.AgreeRef;
import nct.agree.domain.AgreeType;
import nct.agree.service.AgreeHistoryService;
import nct.auction.constant.AuctionStatusCode;
import nct.auction.dto.AuctionBidCreateCommand;
import nct.auction.dto.AuctionBidRequest;
import nct.auction.dto.AuctionBidTarget;
import nct.auction.dto.AuctionBuyNowRequest;
import nct.auction.dto.AuctionDetailResponse;
import nct.auction.dto.AuctionRealtimeEvent;
import nct.auction.dto.AuctionTradeMethodChangeRequest;
import nct.auction.mapper.AuctionMapper;
import nct.auction.service.AuctionEventPublisher;
import nct.auction.service.AuctionService;
import nct.common.domain.RefType;
import nct.favorite.mapper.ProductFavoriteMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.member.dto.BuyerDeliveryAddressSnapshot;
import nct.member.port.BuyerDeliveryAddressReader;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.point.domain.AuctionPolicy;
import nct.point.exception.PointException;
import nct.point.service.PointService;
import nct.product.service.ProductService;
import nct.trade.domain.AuctionTradeSource;
import nct.trade.dto.AuctionTradeCreateCommand;
import nct.trade.dto.AuctionTradeCreateResult;
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
    private BuyerDeliveryAddressReader buyerDeliveryAddressReader;

    @Mock
    private ObjectProvider<ProductService> productServiceProvider;

    @Mock
    private TradeService tradeService;

    @Mock
    private AuctionEventPublisher auctionEventPublisher;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogPort auditLogPort;

    @Mock
    private AgreeHistoryService agreeHistoryService;

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
        target.setProductUseYn("Y");
        target.setEndDateTime(LocalDateTime.now().plusMinutes(2));
        target.setDatabaseNow(LocalDateTime.now());
        lenient().when(auctionMapper.findAuctionBidTargetForUpdate(10L)).thenReturn(target);
        lenient().when(tradeService.createAuctionTrade(any(AuctionTradeCreateCommand.class)))
                .thenReturn(new AuctionTradeCreateResult(900L, "TRDC0003", true));
        lenient().when(buyerDeliveryAddressReader.getOwnedActiveAddressSnapshot(anyLong(), any()))
                .thenReturn(new BuyerDeliveryAddressSnapshot(
                        70L, "구매자", "01012345678", "01234", "서울시 마포구", "101호"));
    }

    @Test
    void placeBidRejectsHiddenProduct() {
        target.setProductUseYn("N");

        assertThatThrownBy(() -> auctionService.placeBid(10L, 40L, bidRequest(12000)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("숨김 처리된 상품");

        verify(auctionMapper, never()).updateAuctionCurrentPrice(any(), any(), any());
    }

    @Test
    void placeBidRejectsAmountNotAlignedWithStoredAuctionBidUnit() {
        target.setBidUnitPrice(BigDecimal.valueOf(3000));
        when(pointService.getAuctionPolicy()).thenReturn(auctionPolicy(3, 2));
        AuctionBidRequest request = bidRequest(12000);

        assertThatThrownBy(() -> auctionService.placeBid(10L, 40L, request))
                .isInstanceOf(CustomException.class);

        verify(auctionMapper, never()).updateAuctionCurrentPrice(any(), any(), any());
    }

    @Test
    void placeBidRejectsAmountNotAlignedWithBidUnit() {
        when(pointService.getAuctionPolicy()).thenReturn(auctionPolicy(3, 2));
        AuctionBidRequest request = bidRequest(11500);

        assertThatThrownBy(() -> auctionService.placeBid(10L, 40L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("입찰 금액은 입찰 단위의 배수여야 합니다.");

        verify(auctionMapper, never()).updateAuctionCurrentPrice(any(), any(), any());
    }

    @Test
    void placeBidPassesPolicyExtensionValuesToMapper() {
        target.setTradeMethodCode("TRDC0009");
        target.setCurrentHighestBidderId(35L);
        target.setCurrentHighestBidId(45L);
        when(pointService.getAuctionPolicy()).thenReturn(auctionPolicy(3, 2));
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

        ArgumentCaptor<AuctionBidCreateCommand> commandCaptor =
                ArgumentCaptor.forClass(AuctionBidCreateCommand.class);
        verify(auctionMapper).insertBid(commandCaptor.capture());
        assertThat(commandCaptor.getValue().getSelectedTradeMethodCode()).isEqualTo("TRDC0009");
        assertThat(commandCaptor.getValue().getSelectedDeliveryAddressId()).isEqualTo(70L);
        verify(buyerDeliveryAddressReader).getOwnedActiveAddressSnapshot(40L, 70L);
        verify(auctionMapper).extendAuctionTime(10L, 3, 2, "40");
        verify(notificationService).notifyBidUpdated(35L, 10L, 12000L);
        ArgumentCaptor<AuditLogCommand> auditCaptor = ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogPort).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().actionCode()).isEqualTo("CREATE");
        assertThat(auditCaptor.getValue().actorId()).isEqualTo("40");
        assertThat(auditCaptor.getValue().referenceTypeCode()).isEqualTo(RefType.BID.getCode());
        assertThat(auditCaptor.getValue().referenceSn()).isEqualTo(50L);
        assertThat(auditCaptor.getValue().reason()).isEqualTo("입찰 성공");
        assertThat(auditCaptor.getValue().beforeSummary()).isEqualTo("auctionAmount=10000P");
        assertThat(auditCaptor.getValue().afterSummary()).isEqualTo("bidAmount=12000P");
        assertThat(auditCaptor.getValue().requestId()).isEqualTo("bid:50");
        assertThat(auditCaptor.getValue().relatedReferenceTypeCode()).isEqualTo(RefType.AUCTION.getCode());
        assertThat(auditCaptor.getValue().relatedReferenceSn()).isEqualTo(10L);
        verifyBidAgreement(50L);
        verifyRealtimeEvent("BID_PLACED");
        assertThat(response.isFavorite()).isTrue();
    }

    /** 담당자 7 · F-OPS-015: 기존 성공 입찰의 재요청은 감사 기록을 추가하지 않습니다. */
    @Test
    void repeatedPlaceBidDoesNotRecordAnotherAudit() {
        target.setTradeMethodCode("TRDC0009");
        when(pointService.getAuctionPolicy()).thenReturn(auctionPolicy(3, 2));
        when(auctionMapper.updateAuctionCurrentPrice(10L, BigDecimal.valueOf(12000), "40")).thenReturn(1);
        when(auctionMapper.insertBid(any(AuctionBidCreateCommand.class))).thenAnswer(invocation -> {
            AuctionBidCreateCommand command = invocation.getArgument(0);
            ReflectionTestUtils.setField(command, "bidId", 50L);
            return 1;
        });
        stubAuctionDetail();

        AuctionBidRequest request = bidRequest(12000);
        auctionService.placeBid(10L, 40L, request);
        target.setCurrentHighestBidderId(40L);
        target.setCurrentHighestBidId(50L);

        assertThatThrownBy(() -> auctionService.placeBid(10L, 40L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        verify(auditLogPort, times(1)).record(any(AuditLogCommand.class));
    }

    @Test
    void placeBidPropagatesPreviousHighestBidHoldReleaseFailure() {
        target.setCurrentHighestBidderId(35L);
        target.setCurrentHighestBidId(45L);
        when(pointService.getAuctionPolicy()).thenReturn(auctionPolicy(3, 2));
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
        target.setTradeMethodCode("TRDC0009");
        target.setInstantBuyPrice(BigDecimal.valueOf(30000));
        target.setCurrentHighestBidderId(35L);
        target.setCurrentHighestBidId(45L);
        when(auctionMapper.insertBid(any(AuctionBidCreateCommand.class))).thenAnswer(invocation -> {
            AuctionBidCreateCommand command = invocation.getArgument(0);
            ReflectionTestUtils.setField(command, "bidId", 50L);
            return 1;
        });
        when(auctionMapper.closeAuctionByInstantBuy(10L, BigDecimal.valueOf(30000), "40")).thenReturn(1);
        stubAuctionDetail();

        AuctionBuyNowRequest request = new AuctionBuyNowRequest();
        request.setDeliveryAddressId(70L);
        AuctionDetailResponse response = auctionService.buyNow(10L, 40L, request);

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
        assertThat(command.getSelectedTradeMethodCode()).isEqualTo("TRDC0009");
        assertThat(command.getSelectedDeliveryAddressId()).isEqualTo(70L);
        assertThat(response.getTradeId()).isEqualTo(900L);
        verify(buyerDeliveryAddressReader).getOwnedActiveAddressSnapshot(40L, 70L);
        verify(notificationService).notifyBidUpdated(35L, 10L, 30000L);
        verify(notificationService).notifyAuctionResult(40L, 10L, true, 900L);
        verifyBidAgreement(50L);
        verifyRealtimeEvent("BUY_NOW");
    }

    @Test
    void buyNowRejectsShippingWithoutPositiveDeliveryAddressIdBeforeMutation() {
        target.setTradeMethodCode("TRDC0009");
        target.setInstantBuyPrice(BigDecimal.valueOf(30000));

        for (Long invalidAddressId : new Long[] { null, 0L, -1L }) {
            AuctionBuyNowRequest request = new AuctionBuyNowRequest();
            request.setDeliveryAddressId(invalidAddressId);

            assertThatThrownBy(() -> auctionService.buyNow(10L, 40L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }

        verify(buyerDeliveryAddressReader, never()).getOwnedActiveAddressSnapshot(anyLong(), any());
        verify(auctionMapper, never()).insertBid(any());
        verify(pointService, never()).hold(anyLong(), anyLong(), any(), anyLong(), any());
        verify(tradeService, never()).createAuctionTrade(any());
        verify(agreeHistoryService, never()).record(anyLong(), any(), any(), anyBoolean(), any());
    }

    @Test
    void placeBidRejectsShippingWithoutDeliveryAddressIdBeforePriceUpdate() {
        target.setTradeMethodCode("TRDC0009");
        AuctionBidRequest request = bidRequest(12000);
        request.setDeliveryAddressId(null);

        assertThatThrownBy(() -> auctionService.placeBid(10L, 40L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(buyerDeliveryAddressReader, never()).getOwnedActiveAddressSnapshot(anyLong(), any());
        verify(auctionMapper, never()).updateAuctionCurrentPrice(any(), any(), any());
        verify(pointService, never()).hold(anyLong(), anyLong(), any(), anyLong(), any());
        verify(agreeHistoryService, never()).record(anyLong(), any(), any(), anyBoolean(), any());
    }

    @Test
    void placeBidRejectsDeliveryAuctionWhenBuyerAddressIsIncomplete() {
        target.setTradeMethodCode("TRDC0009");
        doThrow(new CustomException(ErrorCode.BUYER_ADDRESS_INCOMPLETE))
                .when(buyerDeliveryAddressReader)
                .getOwnedActiveAddressSnapshot(40L, 70L);

        assertThatThrownBy(() -> auctionService.placeBid(10L, 40L, bidRequest(12000)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BUYER_ADDRESS_INCOMPLETE);

        verify(auctionMapper, never()).updateAuctionCurrentPrice(any(), any(), any());
        verify(pointService, never()).hold(anyLong(), anyLong(), any(), anyLong(), any());
    }

    @Test
    void placeBidRejectsMixedAuctionDeliverySelectionWhenBuyerAddressIsIncomplete() {
        target.setTradeMethodCode("TRDC0015");
        AuctionBidRequest request = bidRequest(12000);
        request.setTradeMethod("TRDC0009");
        doThrow(new CustomException(ErrorCode.BUYER_ADDRESS_INCOMPLETE))
                .when(buyerDeliveryAddressReader)
                .getOwnedActiveAddressSnapshot(40L, 70L);

        assertThatThrownBy(() -> auctionService.placeBid(10L, 40L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BUYER_ADDRESS_INCOMPLETE);

        verify(auctionMapper, never()).updateAuctionCurrentPrice(any(), any(), any());
        verify(pointService, never()).hold(anyLong(), anyLong(), any(), anyLong(), any());
    }

    @Test
    void placeBidRejectsMixedAuctionWithoutTradeMethodSelection() {
        target.setTradeMethodCode("TRDC0015");

        assertThatThrownBy(() -> auctionService.placeBid(10L, 40L, bidRequest(12000)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("배송 또는 직거래 방식을 선택해 주세요.");

        verify(auctionMapper, never()).updateAuctionCurrentPrice(any(), any(), any());
    }

    @Test
    void placeBidStoresMixedAuctionOfflineSelectionWithoutCheckingDeliveryAddress() {
        target.setTradeMethodCode("TRDC0015");
        when(pointService.getAuctionPolicy()).thenReturn(auctionPolicy(3, 2));
        when(auctionMapper.updateAuctionCurrentPrice(10L, BigDecimal.valueOf(12000), "40")).thenReturn(1);
        when(auctionMapper.insertBid(any(AuctionBidCreateCommand.class))).thenAnswer(invocation -> {
            AuctionBidCreateCommand command = invocation.getArgument(0);
            ReflectionTestUtils.setField(command, "bidId", 50L);
            return 1;
        });
        stubAuctionDetail();
        AuctionBidRequest request = bidRequest(12000);
        request.setTradeMethod("TRDC0010");

        auctionService.placeBid(10L, 40L, request);

        ArgumentCaptor<AuctionBidCreateCommand> commandCaptor =
                ArgumentCaptor.forClass(AuctionBidCreateCommand.class);
        verify(auctionMapper).insertBid(commandCaptor.capture());
        assertThat(commandCaptor.getValue().getSelectedTradeMethodCode()).isEqualTo("TRDC0010");
        verify(buyerDeliveryAddressReader, never()).getOwnedActiveAddressSnapshot(anyLong(), any());
    }

    @Test
    void currentHighestBidderChangesMixedAuctionTradeMethodToDelivery() {
        target.setTradeMethodCode("TRDC0015");
        target.setCurrentHighestBidId(45L);
        target.setCurrentHighestBidderId(40L);
        target.setCurrentHighestTradeMethodCode("TRDC0010");
        when(auctionMapper.updateCurrentHighestBidTradeMethod(
                10L,
                45L,
                40L,
                "TRDC0009",
                70L,
                "40"))
                .thenReturn(1);
        stubAuctionDetail();

        AuctionDetailResponse response = auctionService.changeCurrentHighestBidTradeMethod(
                10L,
                40L,
                tradeMethodChangeRequest("TRDC0009"));

        assertThat(response).isNotNull();
        verify(buyerDeliveryAddressReader).getOwnedActiveAddressSnapshot(40L, 70L);
        verify(auctionMapper).updateCurrentHighestBidTradeMethod(
                10L,
                45L,
                40L,
                "TRDC0009",
                70L,
                "40");
        verifyRealtimeEvent("BID_TRADE_METHOD_CHANGED");
    }

    @Test
    void currentHighestBidderChangesAddressForDeliveryOnlyAuction() {
        target.setTradeMethodCode("TRDC0009");
        target.setCurrentHighestBidId(45L);
        target.setCurrentHighestBidderId(40L);
        target.setCurrentHighestTradeMethodCode("TRDC0009");
        target.setCurrentHighestDeliveryAddressId(60L);
        when(auctionMapper.updateCurrentHighestBidTradeMethod(
                10L,
                45L,
                40L,
                "TRDC0009",
                70L,
                "40"))
                .thenReturn(1);
        stubAuctionDetail();

        AuctionDetailResponse response = auctionService.changeCurrentHighestBidTradeMethod(
                10L,
                40L,
                tradeMethodChangeRequest("TRDC0009"));

        assertThat(response).isNotNull();
        verify(buyerDeliveryAddressReader).getOwnedActiveAddressSnapshot(40L, 70L);
        verify(auctionMapper).updateCurrentHighestBidTradeMethod(
                10L,
                45L,
                40L,
                "TRDC0009",
                70L,
                "40");
        verifyRealtimeEvent("BID_TRADE_METHOD_CHANGED");
    }

    @Test
    void deliveryOnlyAuctionRejectsChangingTradeMethodToOffline() {
        target.setTradeMethodCode("TRDC0009");
        target.setCurrentHighestBidId(45L);
        target.setCurrentHighestBidderId(40L);
        target.setCurrentHighestTradeMethodCode("TRDC0009");

        assertThatThrownBy(() -> auctionService.changeCurrentHighestBidTradeMethod(
                10L,
                40L,
                tradeMethodChangeRequest("TRDC0010")))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("상품의 거래방식과 선택한 거래방식이 일치하지 않습니다.");

        verify(auctionMapper, never()).updateCurrentHighestBidTradeMethod(
                anyLong(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void tradeMethodChangeRejectsUserWhoIsNotCurrentHighestBidder() {
        target.setTradeMethodCode("TRDC0015");
        target.setCurrentHighestBidId(45L);
        target.setCurrentHighestBidderId(41L);

        assertThatThrownBy(() -> auctionService.changeCurrentHighestBidTradeMethod(
                10L,
                40L,
                tradeMethodChangeRequest("TRDC0009")))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("현재 최고입찰자만 거래방식을 변경할 수 있습니다.");

        verify(auctionMapper, never()).updateCurrentHighestBidTradeMethod(
                anyLong(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void tradeMethodChangeRejectsDeliveryWhenBuyerAddressIsIncomplete() {
        target.setTradeMethodCode("TRDC0015");
        target.setCurrentHighestBidId(45L);
        target.setCurrentHighestBidderId(40L);
        target.setCurrentHighestTradeMethodCode("TRDC0010");
        doThrow(new CustomException(ErrorCode.BUYER_ADDRESS_INCOMPLETE))
                .when(buyerDeliveryAddressReader)
                .getOwnedActiveAddressSnapshot(40L, 70L);

        assertThatThrownBy(() -> auctionService.changeCurrentHighestBidTradeMethod(
                10L,
                40L,
                tradeMethodChangeRequest("TRDC0009")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BUYER_ADDRESS_INCOMPLETE);

        verify(auctionMapper, never()).updateCurrentHighestBidTradeMethod(
                anyLong(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void tradeMethodChangeReturnsCurrentDetailWhenMethodIsUnchanged() {
        target.setTradeMethodCode("TRDC0015");
        target.setCurrentHighestBidId(45L);
        target.setCurrentHighestBidderId(40L);
        target.setCurrentHighestTradeMethodCode("TRDC0010");
        stubAuctionDetail();

        AuctionDetailResponse response = auctionService.changeCurrentHighestBidTradeMethod(
                10L,
                40L,
                tradeMethodChangeRequest("TRDC0010"));

        assertThat(response).isNotNull();
        verify(buyerDeliveryAddressReader, never()).getOwnedActiveAddressSnapshot(anyLong(), any());
        verify(auctionMapper, never()).updateCurrentHighestBidTradeMethod(
                anyLong(), anyLong(), anyLong(), any(), any(), any());
        verify(auctionEventPublisher, never()).publishAfterCommit(any());
    }

    @Test
    void tradeMethodChangeFailsWhenHighestBidChangedDuringUpdate() {
        target.setTradeMethodCode("TRDC0015");
        target.setCurrentHighestBidId(45L);
        target.setCurrentHighestBidderId(40L);
        target.setCurrentHighestTradeMethodCode("TRDC0010");
        when(auctionMapper.updateCurrentHighestBidTradeMethod(
                10L,
                45L,
                40L,
                "TRDC0009",
                70L,
                "40"))
                .thenReturn(0);

        assertThatThrownBy(() -> auctionService.changeCurrentHighestBidTradeMethod(
                10L,
                40L,
                tradeMethodChangeRequest("TRDC0009")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        verify(auctionMapper, never()).findAuctionDetail(10L, 40L);
        verify(auctionEventPublisher, never()).publishAfterCommit(any());
    }

    @Test
    void buyNowRejectsTradeMethodThatDoesNotMatchSingleMethodProduct() {
        target.setInstantBuyPrice(BigDecimal.valueOf(30000));
        AuctionBuyNowRequest request = new AuctionBuyNowRequest();
        request.setTradeMethod("TRDC0009");

        assertThatThrownBy(() -> auctionService.buyNow(10L, 40L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("상품의 거래방식과 선택한 거래방식이 일치하지 않습니다.");

        verify(auctionMapper, never()).insertBid(any());
    }

    @Test
    void buyNowDoesNotNotifyCurrentHighestBidderAsOutbid() {
        target.setInstantBuyPrice(BigDecimal.valueOf(30000));
        target.setCurrentHighestBidderId(40L);
        target.setCurrentHighestBidId(45L);
        when(auctionMapper.insertBid(any(AuctionBidCreateCommand.class))).thenAnswer(invocation -> {
            AuctionBidCreateCommand command = invocation.getArgument(0);
            ReflectionTestUtils.setField(command, "bidId", 50L);
            return 1;
        });
        when(auctionMapper.closeAuctionByInstantBuy(10L, BigDecimal.valueOf(30000), "40")).thenReturn(1);
        stubAuctionDetail();

        auctionService.buyNow(10L, 40L, new AuctionBuyNowRequest());

        verify(buyerDeliveryAddressReader, never()).getOwnedActiveAddressSnapshot(anyLong(), any());
        verify(notificationService, never()).notifyBidUpdated(anyLong(), anyLong(), anyLong());
        verify(notificationService).notifyAuctionResult(40L, 10L, true, 900L);
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
        target.setCurrentHighestTradeMethodCode("TRDC0009");
        target.setCurrentHighestDeliveryAddressId(70L);
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
        assertThat(command.getSelectedTradeMethodCode()).isEqualTo("TRDC0009");
        assertThat(command.getSelectedDeliveryAddressId()).isEqualTo(70L);
        verify(notificationService).notifyAuctionResult(40L, 10L, true, 900L);
        verifyRealtimeEvent("AUCTION_FINALIZED");
    }

    @Test
    void finalizeExpiredAuctionRejectsShippingWinnerWithoutDeliveryAddressIdBeforeMutation() {
        target.setEndDateTime(LocalDateTime.now().minusMinutes(1));
        target.setCurrentHighestBidId(50L);
        target.setCurrentHighestBidderId(40L);
        target.setCurrentHighestTradeMethodCode("TRDC0009");
        target.setCurrentHighestDeliveryAddressId(null);

        assertThatThrownBy(() -> auctionService.finalizeExpiredAuction(10L))
                .isInstanceOf(CustomException.class)
                .hasMessage("배송 거래에는 배송지 번호가 필요합니다.")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(pointService, never()).convertHoldToEscrow(anyLong(), any(), anyLong(), any());
        verify(auctionMapper, never()).updateExpiredAuctionStatus(anyLong(), any(), any());
        verify(tradeService, never()).createAuctionTrade(any(AuctionTradeCreateCommand.class));
    }

    @Test
    void finalizeExpiredAuctionWithoutBidDoesNotCreateTrade() {
        target.setEndDateTime(LocalDateTime.now().minusMinutes(1));
        when(auctionMapper.updateExpiredAuctionStatus(10L, AuctionStatusCode.FAILED, "SYSTEM"))
                .thenReturn(1);

        assertThat(auctionService.finalizeExpiredAuction(10L)).isTrue();

        verify(tradeService, never()).createAuctionTrade(any(AuctionTradeCreateCommand.class));
        verify(notificationService).notifyAuctionFailed(30L, 10L);
        verifyRealtimeEvent("AUCTION_FINALIZED");
    }

    @Test
    void notifyClosingSoonAuctionNotifiesDistinctRecipients() {
        when(auctionMapper.findClosingSoonRecipientUserIds(10L))
                .thenReturn(List.of(40L, 50L, 40L));

        assertThat(auctionService.notifyClosingSoonAuction(10L)).isEqualTo(2);

        verify(notificationService).notifyAuctionClosingSoon(40L, 10L);
        verify(notificationService).notifyAuctionClosingSoon(50L, 10L);
    }

    private void verifyRealtimeEvent(String eventType) {
        ArgumentCaptor<AuctionRealtimeEvent> eventCaptor =
                ArgumentCaptor.forClass(AuctionRealtimeEvent.class);
        verify(auctionEventPublisher).publishAfterCommit(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getAuctionId()).isEqualTo(10L);
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(eventType);
    }

    private void verifyBidAgreement(Long bidId) {
        ArgumentCaptor<AgreeRef> refCaptor = ArgumentCaptor.forClass(AgreeRef.class);
        verify(agreeHistoryService).record(
                eq(40L),
                eq(AgreeType.TERMS_OF_SERVICE),
                eq(AgreeActType.BID),
                eq(true),
                refCaptor.capture());
        assertThat(refCaptor.getValue().getBidSn()).isEqualTo(bidId);
        assertThat(refCaptor.getValue().getAucSn()).isNull();
        assertThat(refCaptor.getValue().getPtLdgSn()).isNull();
        assertThat(refCaptor.getValue().getTrdSn()).isNull();
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
        request.setDeliveryAddressId(70L);
        return request;
    }

    private AuctionTradeMethodChangeRequest tradeMethodChangeRequest(String tradeMethod) {
        AuctionTradeMethodChangeRequest request = new AuctionTradeMethodChangeRequest();
        request.setTradeMethod(tradeMethod);
        request.setDeliveryAddressId(70L);
        return request;
    }

    private AuctionPolicy auctionPolicy(int extensionMinutes, int maxExtensionCount) {
        AuctionPolicy policy = new AuctionPolicy();
        policy.setAucExtMin(extensionMinutes);
        policy.setAucExtMaxCnt(maxExtensionCount);
        return policy;
    }
}
