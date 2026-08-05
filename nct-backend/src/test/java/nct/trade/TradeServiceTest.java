package nct.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.crypto.FieldCryptoService;
import nct.chat.service.ChatService;
import nct.file.service.FileStorageService;
import nct.file.domain.FileMeta;
import nct.member.dto.BuyerAddressSnapshot;
import nct.member.service.MemberService;
import nct.ops.operation.port.SellerCancellationDecision;
import nct.ops.operation.port.SellerCancellationDecisionCommand;
import nct.point.service.PointService;
import nct.settlement.service.SettlementService;
import nct.trade.domain.Trade;
import nct.trade.domain.AuctionTradeSource;
import nct.trade.dto.AuctionTradeCreateCommand;
import nct.trade.dto.AuctionTradeCreateResult;
import nct.trade.dto.AuctionTradeEscrowInfo;
import nct.trade.dto.TradeAutoCompletionTarget;
import nct.trade.dto.TradeCancellationTarget;
import nct.trade.dto.MaterialTradeCreateCommand;
import nct.trade.dto.MaterialTradeCreateResult;
import nct.trade.dto.ServiceTradeCreateCommand;
import nct.trade.dto.ServiceTradeCreateResult;
import nct.trade.dto.ServiceTradeDetailResponse;
import nct.trade.dto.ServiceTradeDetailSource;
import nct.trade.dto.TradeConfirmationTarget;
import nct.trade.dto.TradeDetailResponse;
import nct.trade.dto.TradeDeliveryProofSubmitRequest;
import nct.trade.dto.TradeDeliverySubmitTarget;
import nct.trade.dto.ServiceTradeDisputeRequest;
import nct.trade.dto.ServiceTradeCompletionTarget;
import nct.trade.dto.TradeDisputeTarget;
import nct.trade.dto.TradeListItem;
import nct.trade.dto.TradeOfflineScheduleRequest;
import nct.trade.dto.SellerTradeStatusItem;
import nct.trade.mapper.TradeMapper;
import nct.trade.service.TradeService;
import nct.notification.service.NotificationService;
import nct.setting.domain.SystemSettingDetail;
import nct.setting.mapper.SystemSettingAdminMapper;

class TradeServiceTest {

    private TradeMapper tradeMapper;
    private NotificationService notificationService;
    private SystemSettingAdminMapper systemSettingMapper;
    private FileStorageService fileStorageService;
    private MemberService memberService;
    private SettlementService settlementService;
    private ChatService chatService;
    private PointService pointService;
    private FieldCryptoService fieldCryptoService;
    private TradeService tradeService;

    @BeforeEach
    void setUp() {
        tradeMapper = mock(TradeMapper.class);
        notificationService = mock(NotificationService.class);
        systemSettingMapper = mock(SystemSettingAdminMapper.class);
        fileStorageService = mock(FileStorageService.class);
        memberService = mock(MemberService.class);
        settlementService = mock(SettlementService.class);
        chatService = mock(ChatService.class);
        pointService = mock(PointService.class);
        fieldCryptoService = mock(FieldCryptoService.class);
        when(fieldCryptoService.encrypt(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fieldCryptoService.decrypt(any())).thenAnswer(invocation -> invocation.getArgument(0));
        tradeService = new TradeService(
                tradeMapper,
                notificationService,
                systemSettingMapper,
                fileStorageService,
                memberService,
                settlementService,
                chatService,
                pointService,
                fieldCryptoService);
    }

    @Test
    void createsMaterialTradeAndInitialStatusHistory() {
        MaterialTradeCreateCommand command = new MaterialTradeCreateCommand(
                10L,
                20L,
                30L,
                BigDecimal.valueOf(128000));
        when(tradeMapper.findOwnedProductIdForUpdate(30L, 10L)).thenReturn(30L);
        when(tradeMapper.findMaterialTradeIdByProductId(30L)).thenReturn(null);
        when(tradeMapper.findProductTradeMethod(30L)).thenReturn("TRDC0009");
        when(memberService.getBuyerAddressSnapshot(20L)).thenReturn(
                new BuyerAddressSnapshot("구매자", "01012345678", "01234", "서울시 마포구", "101호"));
        doAnswer(invocation -> {
            Trade trade = invocation.getArgument(0);
            trade.setTrdSn(91L);
            return 1;
        }).when(tradeMapper).insertMaterialTrade(any(Trade.class));

        long tradeId = tradeService.createMaterialTrade(command);

        ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
        verify(tradeMapper).insertMaterialTrade(tradeCaptor.capture());
        assertThat(tradeId).isEqualTo(91L);
        assertThat(tradeCaptor.getValue().getTradeTypeCode()).isEqualTo("TRDC0001");
        assertThat(tradeCaptor.getValue().getTradeStatusCode()).isEqualTo("TRDC0003");
        verify(tradeMapper).insertStatusHistory(
                91L,
                "TRDC0003",
                "낙찰 또는 즉시구매로 거래가 생성되었습니다.");
        verify(tradeMapper).insertDeliverySnapshot(
                91L,
                "구매자",
                "01012345678",
                "01234",
                "서울시 마포구",
                "101호");
    }

    @Test
    void returnsRoleSpecificServiceTradeDetailForRequester() {
        ServiceTradeDetailSource source = new ServiceTradeDetailSource(
                91L,
                10L,
                20L,
                31L,
                "TRDC0003",
                BigDecimal.valueOf(150000),
                null,
                "입주 청소 요청",
                "주방과 욕실 청소 · 150,000원",
                null,
                "ESCROW_HELD",
                "보관금이 안전하게 보관 중입니다.");
        when(tradeMapper.findMyServiceTradeDetail(91L, 10L)).thenReturn(source);

        ServiceTradeDetailResponse response = tradeService.getMyServiceTradeDetail(91L, 10L);

        assertThat(response.tradeId()).isEqualTo(91L);
        assertThat(response.viewerRole()).isEqualTo("REQUESTER");
        assertThat(response.availableActions()).containsExactly(
                "REQUEST_SCHEDULE_CHANGE",
                "REQUEST_SCHEDULE_CANCELLATION",
                "SUBMIT_DISPUTE");
    }

    @Test
    void rejectsUnavailableServiceTradeDetail() {
        when(tradeMapper.findMyServiceTradeDetail(91L, 10L)).thenReturn(null);

        assertThatThrownBy(() -> tradeService.getMyServiceTradeDetail(91L, 10L))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void createsServiceTradeFromServerVerifiedSelectionAndInitialStatusHistory() {
        ServiceTradeCreateCommand command = new ServiceTradeCreateCommand(
                11L, 22L, 31L, 41L, BigDecimal.valueOf(150000));
        when(tradeMapper.findServiceTradeIdByQuoteId(41L)).thenReturn(null);
        doAnswer(invocation -> {
            Trade trade = invocation.getArgument(0);
            trade.setTrdSn(91L);
            return 1;
        }).when(tradeMapper).insertServiceTrade(any(Trade.class));

        ServiceTradeCreateResult result = tradeService.createOrGetServiceTrade(command);

        ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
        verify(tradeMapper).insertServiceTrade(tradeCaptor.capture());
        Trade saved = tradeCaptor.getValue();
        assertThat(result.getTradeId()).isEqualTo(91L);
        assertThat(result.isCreated()).isTrue();
        assertThat(saved.getRequesterUserId()).isEqualTo(11L);
        assertThat(saved.getProviderUserId()).isEqualTo(22L);
        assertThat(saved.getServiceRequestId()).isEqualTo(31L);
        assertThat(saved.getQuoteId()).isEqualTo(41L);
        assertThat(saved.getTradeTypeCode()).isEqualTo("TRDC0002");
        assertThat(saved.getTradeStatusCode()).isEqualTo("TRDC0003");
        verify(tradeMapper).insertStatusHistory(
                91L, "TRDC0003", "선택 견적으로 서비스 거래가 생성되었습니다.");
    }

    @Test
    void returnsExistingServiceTradeForDuplicateSelectedQuote() {
        ServiceTradeCreateCommand command = new ServiceTradeCreateCommand(
                11L, 22L, 31L, 41L, BigDecimal.valueOf(150000));
        when(tradeMapper.findServiceTradeIdByQuoteId(41L)).thenReturn(91L);

        ServiceTradeCreateResult result = tradeService.createOrGetServiceTrade(command);

        assertThat(result.getTradeId()).isEqualTo(91L);
        assertThat(result.isCreated()).isFalse();
        verify(tradeMapper, never()).insertServiceTrade(any(Trade.class));
        verify(tradeMapper, never()).insertStatusHistory(anyLong(), any(), any());
    }

    @Test
    void rejectsServiceTradeWhenRequesterAndProviderAreSame() {
        ServiceTradeCreateCommand command = new ServiceTradeCreateCommand(
                11L, 11L, 31L, 41L, BigDecimal.valueOf(150000));

        assertThatThrownBy(() -> tradeService.createOrGetServiceTrade(command))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(tradeMapper);
    }

    @Test
    void registersServiceTradeDisputeAndHoldsPendingSettlementInOneFlow() {
        TradeDisputeTarget target = new TradeDisputeTarget();
        target.setTradeSn(81L);
        target.setRequesterUserId(11L);
        target.setProviderUserId(22L);
        target.setTradeTypeCode("TRDC0002");
        target.setTradeStatusCode("TRDC0005");
        ServiceTradeDisputeRequest request = new ServiceTradeDisputeRequest();
        request.setDisputeTypeCode("TRDC0011");
        request.setContent("작업 완료 내용에 이견이 있습니다.");
        when(tradeMapper.findTradeDisputeTargetForUpdate(81L)).thenReturn(target);
        when(tradeMapper.hasOpenTradeDispute(81L)).thenReturn(false);
        when(tradeMapper.holdServiceTradeForDispute(81L, "11")).thenReturn(1);

        tradeService.registerServiceTradeDispute(81L, 11L, request);

        verify(tradeMapper).insertTradeDispute(
                81L, 11L, "TRDC0011", "작업 완료 내용에 이견이 있습니다.", "11");
        verify(settlementService).holdUpByTradeIfPending(81L, "거래 문제 접수");
        verify(chatService).closeServiceTradeChatRoom(81L);
        verify(tradeMapper).insertStatusHistory(81L, "TRDC0007", "거래 문제가 접수되었습니다.");
    }

    @Test
    void rejectsDuplicateOpenServiceTradeDispute() {
        TradeDisputeTarget target = new TradeDisputeTarget();
        target.setTradeSn(81L);
        target.setRequesterUserId(11L);
        target.setProviderUserId(22L);
        target.setTradeTypeCode("TRDC0002");
        target.setTradeStatusCode("TRDC0003");
        ServiceTradeDisputeRequest request = new ServiceTradeDisputeRequest();
        request.setDisputeTypeCode("TRDC0011");
        request.setContent("작업이 시작되지 않았습니다.");
        when(tradeMapper.findTradeDisputeTargetForUpdate(81L)).thenReturn(target);
        when(tradeMapper.hasOpenTradeDispute(81L)).thenReturn(true);

        assertThatThrownBy(() -> tradeService.registerServiceTradeDispute(81L, 11L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_PROCESSED);

        verify(settlementService, never()).holdUpByTradeIfPending(anyLong(), any());
    }

    @Test
    void rejectsServiceTradeDisputeFromNonPartyUser() {
        TradeDisputeTarget target = new TradeDisputeTarget();
        target.setTradeSn(81L);
        target.setRequesterUserId(11L);
        target.setProviderUserId(22L);
        target.setTradeTypeCode("TRDC0002");
        target.setTradeStatusCode("TRDC0003");
        ServiceTradeDisputeRequest request = new ServiceTradeDisputeRequest();
        request.setDisputeTypeCode("TRDC0011");
        request.setContent("제3자 접수 시도");
        when(tradeMapper.findTradeDisputeTargetForUpdate(81L)).thenReturn(target);

        assertThatThrownBy(() -> tradeService.registerServiceTradeDispute(81L, 99L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_RESOURCE_OWNER);

        verify(tradeMapper, never()).hasOpenTradeDispute(anyLong());
        verify(settlementService, never()).holdUpByTradeIfPending(anyLong(), any());
    }

    @Test
    void rejectsServiceTradeDisputeAfterCompletion() {
        TradeDisputeTarget target = new TradeDisputeTarget();
        target.setTradeSn(81L);
        target.setRequesterUserId(11L);
        target.setProviderUserId(22L);
        target.setTradeTypeCode("TRDC0002");
        target.setTradeStatusCode("TRDC0006");
        ServiceTradeDisputeRequest request = new ServiceTradeDisputeRequest();
        request.setDisputeTypeCode("TRDC0011");
        request.setContent("완료 후 접수 시도");
        when(tradeMapper.findTradeDisputeTargetForUpdate(81L)).thenReturn(target);

        assertThatThrownBy(() -> tradeService.registerServiceTradeDispute(81L, 11L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        verify(tradeMapper, never()).hasOpenTradeDispute(anyLong());
        verify(settlementService, never()).holdUpByTradeIfPending(anyLong(), any());
    }

    @Test
    void providerRequestsServiceCompletionAndStartsFiveDayConfirmation() {
        ServiceTradeCompletionTarget target = serviceCompletionTarget("TRDC0003", null);
        SystemSettingDetail setting = new SystemSettingDetail();
        setting.setTrdCfmnDays(5);
        when(tradeMapper.findServiceTradeCompletionTargetForUpdate(81L)).thenReturn(target);
        when(tradeMapper.hasOpenTradeDispute(81L)).thenReturn(false);
        when(systemSettingMapper.selectOne()).thenReturn(setting);
        when(tradeMapper.startServiceCompletionRequest(anyLong(), any(), any())).thenReturn(1);

        tradeService.requestServiceCompletion(81L, 22L, "  에어컨 분해 청소와 시운전을 완료했습니다.  ");

        verify(tradeMapper).startServiceCompletionRequest(eq(81L), any(), eq("22"));
        verify(tradeMapper).insertStatusHistory(81L, "TRDC0005", "에어컨 분해 청소와 시운전을 완료했습니다.");
        verify(notificationService).notifyTradeConfirmRequest(11L, 81L, 5);
    }

    @Test
    void rejectsBlankServiceCompletionMemoBeforeChangingTradeState() {
        assertThatThrownBy(() -> tradeService.requestServiceCompletion(81L, 22L, "   "))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(tradeMapper, notificationService);
    }

    @Test
    void requesterConfirmationCompletesServiceTradeAndSettlesProviderEscrow() {
        ServiceTradeCompletionTarget target = serviceCompletionTarget("TRDC0005", LocalDateTime.now().plusDays(5));
        when(tradeMapper.findServiceTradeCompletionTargetForUpdate(81L)).thenReturn(target);
        when(tradeMapper.hasOpenTradeDispute(81L)).thenReturn(false);
        when(tradeMapper.completeServiceTrade(81L, "11")).thenReturn(1);
        when(settlementService.createPending(81L, 22L, 150000L)).thenReturn(61L);

        tradeService.confirmServiceCompletion(81L, 11L);

        verify(settlementService).createPending(81L, 22L, 150000L);
        verify(settlementService).completeAutomatically(61L);
        verify(chatService).closeServiceTradeChatRoom(81L);
        verify(tradeMapper).insertStatusHistory(81L, "TRDC0006", "서비스 의뢰자가 완료를 확인했습니다.");
        verify(notificationService).notifyTradeComplete(11L, 81L, false);
        verify(notificationService).notifyTradeComplete(22L, 81L, false);
    }

    @Test
    void expiredServiceConfirmationClosesChatRoomAfterAutomaticCompletion() {
        ServiceTradeCompletionTarget target = serviceCompletionTarget("TRDC0005", LocalDateTime.now().minusMinutes(1));
        when(tradeMapper.findServiceTradeCompletionTargetForUpdate(81L)).thenReturn(target);
        when(tradeMapper.hasOpenTradeDispute(81L)).thenReturn(false);
        when(tradeMapper.completeServiceTrade(81L, "SYSTEM")).thenReturn(1);
        when(settlementService.createPending(81L, 22L, 150000L)).thenReturn(61L);

        boolean completed = tradeService.completeExpiredServiceConfirmation(81L, LocalDateTime.now());

        assertThat(completed).isTrue();
        verify(chatService).closeServiceTradeChatRoom(81L);
        verify(notificationService).notifyTradeComplete(11L, 81L, true);
        verify(notificationService).notifyTradeComplete(22L, 81L, true);
    }

    @Test
    void expiredServiceCompletionDoesNotSettleWhenOpenDisputeExists() {
        ServiceTradeCompletionTarget target = serviceCompletionTarget("TRDC0005", LocalDateTime.now().minusMinutes(1));
        when(tradeMapper.findServiceTradeCompletionTargetForUpdate(81L)).thenReturn(target);
        when(tradeMapper.hasOpenTradeDispute(81L)).thenReturn(true);

        assertThatThrownBy(() -> tradeService.completeExpiredServiceConfirmation(81L, LocalDateTime.now()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        verify(tradeMapper, never()).completeServiceTrade(anyLong(), any());
        verify(settlementService, never()).createPending(anyLong(), anyLong(), anyLong());
    }

    private ServiceTradeCompletionTarget serviceCompletionTarget(
            String status,
            LocalDateTime autoCompleteAt) {
        ServiceTradeCompletionTarget target = new ServiceTradeCompletionTarget();
        target.setTradeId(81L);
        target.setRequesterUserId(11L);
        target.setProviderUserId(22L);
        target.setTradeAmount(BigDecimal.valueOf(150000L));
        target.setTradeStatus(status);
        target.setAutoCompleteAt(autoCompleteAt);
        return target;
    }

    @Test
    void createsAuctionTradeWithBuyNowSource() {
        AuctionTradeCreateCommand command = new AuctionTradeCreateCommand(
                40L,
                30L,
                50L,
                10L,
                20L,
                BigDecimal.valueOf(128000),
                AuctionTradeSource.BUY_NOW);
        when(tradeMapper.findOwnedProductIdForUpdate(30L, 10L)).thenReturn(30L);
        when(tradeMapper.findMaterialTradeIdByProductId(30L)).thenReturn(null);
        when(tradeMapper.findProductTradeMethod(30L)).thenReturn("TRDC0010");
        doAnswer(invocation -> {
            Trade trade = invocation.getArgument(0);
            trade.setTrdSn(91L);
            return 1;
        }).when(tradeMapper).insertMaterialTrade(any(Trade.class));

        AuctionTradeCreateResult result = tradeService.createAuctionTrade(command);

        assertThat(result.getTradeSn()).isEqualTo(91L);
        assertThat(result.isCreated()).isTrue();
        assertThat(result.isExistingTrade()).isFalse();
        ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
        verify(tradeMapper).insertMaterialTrade(tradeCaptor.capture());
        assertThat(tradeCaptor.getValue().getBidId()).isEqualTo(50L);
        verify(tradeMapper).insertStatusHistory(
                91L,
                "TRDC0003",
                "즉시구매로 거래가 생성되었습니다.");
    }

    @Test
    void createsDeliveryTradeForBothMethodProductWhenFinalMethodIsSelected() {
        AuctionTradeCreateCommand command = new AuctionTradeCreateCommand(
                40L, 30L, 50L, 10L, 20L, BigDecimal.valueOf(128000),
                AuctionTradeSource.BUY_NOW, "TRDC0009");
        when(tradeMapper.findOwnedProductIdForUpdate(30L, 10L)).thenReturn(30L);
        when(tradeMapper.findMaterialTradeIdByProductId(30L)).thenReturn(null);
        when(tradeMapper.findProductTradeMethod(30L)).thenReturn("TRDC0020");
        when(memberService.getBuyerAddressSnapshot(20L)).thenReturn(
                new BuyerAddressSnapshot("구매자", "01012345678", "01234", "서울시 마포구", "101호"));
        doAnswer(invocation -> {
            Trade trade = invocation.getArgument(0);
            trade.setTrdSn(91L);
            return 1;
        }).when(tradeMapper).insertMaterialTrade(any(Trade.class));

        tradeService.createAuctionTrade(command);

        ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
        verify(tradeMapper).insertMaterialTrade(tradeCaptor.capture());
        assertThat(tradeCaptor.getValue().getTradeMethodCode()).isEqualTo("TRDC0009");
        verify(tradeMapper).insertDeliverySnapshot(
                91L, "구매자", "01012345678", "01234", "서울시 마포구", "101호");
    }

    @Test
    void rejectsBothMethodProductWithoutFinalMethodSelection() {
        AuctionTradeCreateCommand command = new AuctionTradeCreateCommand(
                40L, 30L, 50L, 10L, 20L, BigDecimal.valueOf(128000),
                AuctionTradeSource.BUY_NOW);
        when(tradeMapper.findOwnedProductIdForUpdate(30L, 10L)).thenReturn(30L);
        when(tradeMapper.findMaterialTradeIdByProductId(30L)).thenReturn(null);
        when(tradeMapper.findProductTradeMethod(30L)).thenReturn("TRDC0020");

        assertThatThrownBy(() -> tradeService.createAuctionTrade(command))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(tradeMapper, never()).insertMaterialTrade(any(Trade.class));
    }

    @Test
    void rejectsAuctionTradeWithoutWinningBid() {
        AuctionTradeCreateCommand command = new AuctionTradeCreateCommand(
                40L,
                30L,
                0L,
                10L,
                20L,
                BigDecimal.valueOf(128000),
                AuctionTradeSource.AUCTION_WIN);

        assertThatThrownBy(() -> tradeService.createAuctionTrade(command))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verifyNoInteractions(tradeMapper);
    }

    @Test
    void returnsAuctionTradeEscrowInfoByProductId() {
        AuctionTradeEscrowInfo info = new AuctionTradeEscrowInfo();
        info.setTradeSn(91L);
        info.setBidSn(501L);
        info.setBuyerUsrSn(20L);
        info.setTradeStatusCd("TRDC0003");
        info.setTradeAmount(BigDecimal.valueOf(128000));
        when(tradeMapper.findAuctionTradeEscrowInfoByProductId(30L)).thenReturn(info);

        assertThat(tradeService.findAuctionTradeEscrowInfoByProductId(30L))
                .containsSame(info);
    }

    @Test
    void returnsEmptyWhenAuctionTradeDoesNotExist() {
        when(tradeMapper.findAuctionTradeEscrowInfoByProductId(30L)).thenReturn(null);

        assertThat(tradeService.findAuctionTradeEscrowInfoByProductId(30L)).isEmpty();
    }

    @Test
    void createsOfflineTradeWithoutDeliverySnapshot() {
        MaterialTradeCreateCommand command = new MaterialTradeCreateCommand(
                10L,
                20L,
                30L,
                BigDecimal.valueOf(128000));
        when(tradeMapper.findOwnedProductIdForUpdate(30L, 10L)).thenReturn(30L);
        when(tradeMapper.findMaterialTradeIdByProductId(30L)).thenReturn(null);
        when(tradeMapper.findProductTradeMethod(30L)).thenReturn("TRDC0010");
        doAnswer(invocation -> {
            Trade trade = invocation.getArgument(0);
            trade.setTrdSn(91L);
            return 1;
        }).when(tradeMapper).insertMaterialTrade(any(Trade.class));

        MaterialTradeCreateResult result = tradeService.createOrGetMaterialTrade(command);

        assertThat(result.isCreated()).isTrue();
        verifyNoInteractions(memberService);
        verify(tradeMapper, never()).insertDeliverySnapshot(
                anyLong(),
                any(),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void propagatesIncompleteBuyerAddressForAuctionTransactionRollback() {
        MaterialTradeCreateCommand command = new MaterialTradeCreateCommand(
                10L,
                20L,
                30L,
                BigDecimal.valueOf(128000));
        when(tradeMapper.findOwnedProductIdForUpdate(30L, 10L)).thenReturn(30L);
        when(tradeMapper.findMaterialTradeIdByProductId(30L)).thenReturn(null);
        when(tradeMapper.findProductTradeMethod(30L)).thenReturn("TRDC0009");
        doAnswer(invocation -> {
            Trade trade = invocation.getArgument(0);
            trade.setTrdSn(91L);
            return 1;
        }).when(tradeMapper).insertMaterialTrade(any(Trade.class));
        when(memberService.getBuyerAddressSnapshot(20L)).thenThrow(
                new CustomException(ErrorCode.BUYER_ADDRESS_INCOMPLETE));

        assertThatThrownBy(() -> tradeService.createOrGetMaterialTrade(command))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BUYER_ADDRESS_INCOMPLETE);
        verify(tradeMapper, never()).insertDeliverySnapshot(
                anyLong(),
                any(),
                any(),
                any(),
                any(),
                any());
        verify(tradeMapper, never()).insertStatusHistory(anyLong(), any(), any());
    }

    @Test
    void returnsExistingTradeForDuplicateProductCreation() {
        MaterialTradeCreateCommand command = new MaterialTradeCreateCommand(
                10L,
                20L,
                30L,
                BigDecimal.valueOf(128000));
        when(tradeMapper.findOwnedProductIdForUpdate(30L, 10L)).thenReturn(30L);
        when(tradeMapper.findMaterialTradeIdByProductId(30L)).thenReturn(91L);

        MaterialTradeCreateResult result = tradeService.createOrGetMaterialTrade(command);

        assertThat(result.getTradeId()).isEqualTo(91L);
        assertThat(result.getTradeStatusCode()).isEqualTo("TRDC0003");
        assertThat(result.isCreated()).isFalse();
        verify(tradeMapper, never()).insertMaterialTrade(any(Trade.class));
        verify(tradeMapper, never()).insertStatusHistory(anyLong(), any(), any());
    }

    @Test
    void returnsOnlyMapperResultsForCurrentUser() {
        TradeListItem item = new TradeListItem();
        item.setTradeId(91L);
        when(tradeMapper.findMyMaterialTrades(10L, null, null, null)).thenReturn(List.of(item));

        List<TradeListItem> result = tradeService.getMyMaterialTrades(10L);

        assertThat(result).containsExactly(item);
        verify(tradeMapper).findMyMaterialTrades(10L, null, null, null);
    }

    @Test
    void returnsOnlyCurrentSellersCreatedTradeStatuses() {
        SellerTradeStatusItem item = new SellerTradeStatusItem();
        item.setPrdSn(30L);
        item.setTradeSn(91L);
        item.setTradeStatusCd("TRDC0004");
        when(tradeMapper.findMySellerTradeStatuses(10L)).thenReturn(List.of(item));

        List<SellerTradeStatusItem> result = tradeService.getMySellerTradeStatuses(10L);

        assertThat(result).containsExactly(item);
        verify(tradeMapper).findMySellerTradeStatuses(10L);
    }

    @Test
    void returnsTradeStatusesForDistinctProductIds() {
        SellerTradeStatusItem item = new SellerTradeStatusItem();
        item.setPrdSn(30L);
        item.setTradeSn(91L);
        item.setTradeStatusCd("TRDC0004");
        when(tradeMapper.findTradeStatusesByProducts(List.of(30L, 40L)))
                .thenReturn(List.of(item));

        List<SellerTradeStatusItem> result = tradeService.getTradeStatusesByProducts(
                List.of(30L, 40L, 30L));

        assertThat(result).containsExactly(item);
        verify(tradeMapper).findTradeStatusesByProducts(List.of(30L, 40L));
    }

    @Test
    void returnsEmptyTradeStatusListWhenProductIdsAreEmpty() {
        assertThat(tradeService.getTradeStatusesByProducts(null)).isEmpty();
        assertThat(tradeService.getTradeStatusesByProducts(List.of())).isEmpty();
        verifyNoInteractions(tradeMapper);
    }

    @Test
    void rejectsTradeDetailOutsideCurrentUsersTransactions() {
        when(tradeMapper.findMyMaterialTradeDetail(anyLong(), anyLong())).thenReturn(null);

        assertThatThrownBy(() -> tradeService.getMyMaterialTradeDetail(91L, 10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void returnsCurrentUsersTradeDetail() {
        TradeDetailResponse detail = new TradeDetailResponse();
        detail.setTradeId(91L);
        detail.setRecipientName("암호화된 구매자");
        detail.setRecipientPhone("암호화된 연락처");
        detail.setDeliveryAddress("암호화된 기본주소");
        detail.setDeliveryDetailAddress("암호화된 상세주소");
        when(tradeMapper.findMyMaterialTradeDetail(91L, 10L)).thenReturn(detail);
        when(fieldCryptoService.decrypt("암호화된 구매자")).thenReturn("구매자");
        when(fieldCryptoService.decrypt("암호화된 연락처")).thenReturn("01012345678");
        when(fieldCryptoService.decrypt("암호화된 기본주소")).thenReturn("서울시 마포구");
        when(fieldCryptoService.decrypt("암호화된 상세주소")).thenReturn("101호");

        TradeDetailResponse result = tradeService.getMyMaterialTradeDetail(91L, 10L);

        assertThat(result).isSameAs(detail);
        assertThat(result.getRecipientName()).isEqualTo("구매자");
        assertThat(result.getRecipientPhone()).isEqualTo("01012345678");
        assertThat(result.getDeliveryAddress()).isEqualTo("서울시 마포구 101호");
        assertThat(result.getDeliveryDetailAddress()).isEqualTo("101호");
    }

    @Test
    void submitsDeliveryProofAndStartsDeliveryInOneFlow() {
        TradeDeliverySubmitTarget target = deliveryTarget("TRDC0003", 501L);
        TradeDeliveryProofSubmitRequest request = deliveryProofRequest(List.of(801L, 802L));
        TradeDetailResponse detail = deliveryDetail(91L, 501L);

        when(tradeMapper.findMyDeliveryTradeForUpdate(91L, 10L)).thenReturn(target);
        when(fileStorageService.requireOwnedActiveFile(801L, 10L))
                .thenReturn(deliveryFile(801L));
        when(fileStorageService.requireOwnedActiveFile(802L, 10L))
                .thenReturn(deliveryFile(802L));
        when(tradeMapper.startDelivery(91L, "10")).thenReturn(1);
        when(tradeMapper.findMyMaterialTradeDetail(91L, 10L)).thenReturn(detail);
        when(tradeMapper.findTradeDeliveryProofFiles(501L)).thenReturn(List.of());

        TradeDetailResponse result = tradeService.submitDeliveryProof(91L, 10L, request);

        verify(tradeMapper).updateDeliveryMessage(501L, "포장 후 발송했습니다.", "10");
        verify(tradeMapper).insertTradeDeliveryFile(501L, 801L, 1);
        verify(tradeMapper).insertTradeDeliveryFile(501L, 802L, 2);
        verify(tradeMapper).startDelivery(91L, "10");
        verify(tradeMapper).insertStatusHistory(
                91L,
                "TRDC0004",
                "판매자가 발송 인증사진과 배송 메모를 등록했습니다.");
        verify(notificationService).notifyDeliveryStart(20L, 91L);
        assertThat(result).isSameAs(detail);
    }

    @Test
    void rejectsDeliveryProofThatUsesNonDeliveryFile() {
        TradeDeliverySubmitTarget target = deliveryTarget("TRDC0003", 501L);
        TradeDeliveryProofSubmitRequest request = deliveryProofRequest(List.of(801L));
        FileMeta productFile = FileMeta.builder()
                .flSn(801L)
                .flPath("/api/attachment/product/20260721/product.jpg")
                .build();

        when(tradeMapper.findMyDeliveryTradeForUpdate(91L, 10L)).thenReturn(target);
        when(fileStorageService.requireOwnedActiveFile(801L, 10L)).thenReturn(productFile);

        assertThatThrownBy(() -> tradeService.submitDeliveryProof(91L, 10L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(tradeMapper, never()).updateDeliveryMessage(anyLong(), any(), any());
        verify(tradeMapper, never()).startDelivery(anyLong(), any());
    }

    @Test
    void rejectsDuplicateDeliveryProofFilesBeforeSaving() {
        TradeDeliverySubmitTarget target = deliveryTarget("TRDC0003", 501L);
        TradeDeliveryProofSubmitRequest request = deliveryProofRequest(List.of(801L, 801L));

        when(tradeMapper.findMyDeliveryTradeForUpdate(91L, 10L)).thenReturn(target);

        assertThatThrownBy(() -> tradeService.submitDeliveryProof(91L, 10L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verifyNoInteractions(fileStorageService);
        verify(tradeMapper, never()).updateDeliveryMessage(anyLong(), any(), any());
    }

    @Test
    void rejectsDeliveryProofMemoLongerThanFiveHundredCharacters() {
        TradeDeliveryProofSubmitRequest request = deliveryProofRequest(List.of(801L));
        request.setDeliveryMessage("가".repeat(501));

        assertThatThrownBy(() -> tradeService.submitDeliveryProof(91L, 10L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(tradeMapper, never()).findMyDeliveryTradeForUpdate(anyLong(), anyLong());
    }

    @Test
    void rejectsDeliveryProofWhenTradeIsAlreadyDelivering() {
        TradeDeliverySubmitTarget target = deliveryTarget("TRDC0004", 501L);
        TradeDeliveryProofSubmitRequest request = deliveryProofRequest(List.of(801L));

        when(tradeMapper.findMyDeliveryTradeForUpdate(91L, 10L)).thenReturn(target);

        assertThatThrownBy(() -> tradeService.submitDeliveryProof(91L, 10L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_PROCESSED);
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void rollsBackDeliveryProofWhenStatusTransitionFails() {
        TradeDeliverySubmitTarget target = deliveryTarget("TRDC0003", 501L);
        TradeDeliveryProofSubmitRequest request = deliveryProofRequest(List.of(801L));

        when(tradeMapper.findMyDeliveryTradeForUpdate(91L, 10L)).thenReturn(target);
        when(fileStorageService.requireOwnedActiveFile(801L, 10L)).thenReturn(deliveryFile(801L));
        when(tradeMapper.startDelivery(91L, "10")).thenReturn(0);

        assertThatThrownBy(() -> tradeService.submitDeliveryProof(91L, 10L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
        verify(tradeMapper, never()).insertStatusHistory(anyLong(), any(), any());
    }

    @Test
    void normalizesListFiltersBeforeQueryingMyTrades() {
        when(tradeMapper.findMyMaterialTrades(
                10L,
                "BUYER",
                "TRDC0005",
                "테스트 상품")).thenReturn(List.of());

        List<TradeListItem> result = tradeService.getMyMaterialTrades(
                10L,
                "buyer",
                "WAITING_CONFIRMATION",
                "  테스트 상품  ");

        assertThat(result).isEmpty();
        verify(tradeMapper).findMyMaterialTrades(
                10L,
                "BUYER",
                "TRDC0005",
                "테스트 상품");
    }

    @Test
    void rejectsUnsupportedTradeStatusFilter() {
        assertThatThrownBy(() -> tradeService.getMyMaterialTrades(
                10L,
                "ALL",
                "UNKNOWN",
                null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void savesSellersOfflineScheduleAndReturnsUpdatedDetail() {
        TradeOfflineScheduleRequest request = new TradeOfflineScheduleRequest();
        request.setMeetingDate(LocalDate.now().plusDays(1));
        request.setMeetingTime(LocalTime.of(14, 30));
        request.setMeetingPlace("합정역 8번 출구 앞");
        request.setMeetingAddress("서울 마포구 양화로 45");
        TradeDetailResponse detail = new TradeDetailResponse();
        detail.setTradeId(91L);
        when(tradeMapper.findMyOfflineTradeIdForUpdate(91L, 10L)).thenReturn(91L);
        when(tradeMapper.startOfflineTrade(91L, "10")).thenReturn(1);
        when(tradeMapper.findMyMaterialTradeDetail(91L, 10L)).thenReturn(detail);

        TradeDetailResponse result = tradeService.saveMyOfflineSchedule(91L, 10L, request);

        verify(tradeMapper).upsertOfflineSchedule(
                91L,
                LocalDateTime.of(request.getMeetingDate(), request.getMeetingTime()),
                "합정역 8번 출구 앞",
                "서울 마포구 양화로 45");
        verify(tradeMapper).startOfflineTrade(91L, "10");
        verify(tradeMapper).insertStatusHistory(
                91L,
                "TRDC0004",
                "판매자가 직거래 일정을 제안했습니다.");
        verify(chatService).createOrGetOfflineTradeChatRoom(91L);
        assertThat(result).isSameAs(detail);
    }

    @Test
    void rejectsPastOfflineScheduleBeforeDatabaseAccess() {
        TradeOfflineScheduleRequest request = new TradeOfflineScheduleRequest();
        request.setMeetingDate(LocalDate.now().minusDays(1));
        request.setMeetingTime(LocalTime.NOON);
        request.setMeetingPlace("합정역 8번 출구 앞");

        assertThatThrownBy(() -> tradeService.saveMyOfflineSchedule(91L, 10L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verifyNoInteractions(tradeMapper);
    }

    @Test
    void rejectsPastTimeOnTodayOfflineScheduleBeforeDatabaseAccess() {
        TradeOfflineScheduleRequest request = new TradeOfflineScheduleRequest();
        request.setMeetingDate(LocalDate.now());
        request.setMeetingTime(LocalTime.MIDNIGHT);
        request.setMeetingPlace("합정역 8번 출구 앞");

        assertThatThrownBy(() -> tradeService.saveMyOfflineSchedule(91L, 10L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verifyNoInteractions(tradeMapper);
    }

    @Test
    void buyerStartsCompletionConfirmationAndNotifiesSeller() {
        TradeConfirmationTarget target = new TradeConfirmationTarget();
        target.setTradeId(91L);
        target.setBuyerUserId(20L);
        target.setSellerUserId(10L);
        target.setTradeStatus("TRDC0004");
        target.setTradeMethod("TRDC0009");
        TradeDetailResponse detail = new TradeDetailResponse();
        detail.setTradeId(91L);
        SystemSettingDetail setting = new SystemSettingDetail();
        setting.setTrdCfmnDays(5);

        when(tradeMapper.findMyTradeForConfirmationForUpdate(91L, 20L))
                .thenReturn(target);
        when(systemSettingMapper.selectOne()).thenReturn(setting);
        when(tradeMapper.startCompletionConfirmation(
                eq(91L), any(LocalDateTime.class), eq("20"))).thenReturn(1);
        when(tradeMapper.findMyMaterialTradeDetail(91L, 20L)).thenReturn(detail);

        TradeDetailResponse result = tradeService.requestCompletionConfirmation(91L, 20L);

        ArgumentCaptor<LocalDateTime> deadlineCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tradeMapper).startCompletionConfirmation(
                org.mockito.ArgumentMatchers.eq(91L),
                deadlineCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("20"));
        assertThat(deadlineCaptor.getValue())
                .isAfter(LocalDateTime.now().plusDays(5).minusMinutes(1));
        verify(tradeMapper).insertStatusHistory(
                91L,
                "TRDC0005",
                "구매자가 거래 완료 확인을 요청했습니다.");
        verify(notificationService).notifyTradeConfirmRequest(10L, 91L, 5);
        assertThat(result).isSameAs(detail);
    }

    @Test
    void rejectsOfflineCompletionRequestBeforeScheduleIsSaved() {
        TradeConfirmationTarget target = new TradeConfirmationTarget();
        target.setTradeId(91L);
        target.setTradeStatus("TRDC0003");
        target.setTradeMethod("TRDC0010");
        when(tradeMapper.findMyTradeForConfirmationForUpdate(91L, 20L))
                .thenReturn(target);
        when(tradeMapper.hasOfflineSchedule(91L)).thenReturn(false);

        assertThatThrownBy(() -> tradeService.requestCompletionConfirmation(91L, 20L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(tradeMapper, never()).startCompletionConfirmation(
                anyLong(), any(), org.mockito.ArgumentMatchers.anyString());
        verifyNoInteractions(systemSettingMapper, notificationService);
    }

    @Test
    void rejectsCompletionRequestWhenAlreadyWaitingForConfirmation() {
        TradeConfirmationTarget target = new TradeConfirmationTarget();
        target.setTradeStatus("TRDC0005");
        target.setCompletionRequesterId("20");
        when(tradeMapper.findMyTradeForConfirmationForUpdate(91L, 20L))
                .thenReturn(target);

        assertThatThrownBy(() -> tradeService.requestCompletionConfirmation(91L, 20L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_PROCESSED);
        verifyNoInteractions(systemSettingMapper, notificationService);
    }

    @Test
    void completesTradeWhenCounterpartConfirmsAfterSellerRequest() {
        TradeConfirmationTarget target = new TradeConfirmationTarget();
        target.setTradeId(91L);
        target.setSellerUserId(10L);
        target.setBuyerUserId(20L);
        target.setTradeStatus("TRDC0005");
        target.setCompletionRequesterId("10");
        target.setTradeAmount(BigDecimal.valueOf(30000L));
        TradeDetailResponse detail = new TradeDetailResponse();
        detail.setTradeId(91L);

        when(tradeMapper.findMyTradeForConfirmationForUpdate(91L, 20L))
                .thenReturn(target);
        when(tradeMapper.completeConfirmationByCounterpart(91L, "10", "20"))
                .thenReturn(1);
        when(settlementService.createPending(91L, 10L, 30000L)).thenReturn(501L);
        when(tradeMapper.findMyMaterialTradeDetail(91L, 20L)).thenReturn(detail);

        TradeDetailResponse result = tradeService.requestCompletionConfirmation(91L, 20L);

        verify(settlementService).createPending(91L, 10L, 30000L);
        verify(settlementService).completeAutomatically(501L);
        verify(chatService).closeOfflineTradeChatRoom(91L);
        verify(tradeMapper).insertStatusHistory(
                91L,
                "TRDC0006",
                "구매자와 판매자가 모두 거래 완료를 확인했습니다.");
        verify(notificationService).notifyTradeComplete(20L, 91L, false);
        verify(notificationService).notifyTradeComplete(10L, 91L, false);
        assertThat(result).isSameAs(detail);
    }

    @Test
    void completesExpiredConfirmationAndNotifiesBothParties() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 12, 0);
        TradeAutoCompletionTarget target = new TradeAutoCompletionTarget();
        target.setTradeId(91L);
        target.setSellerUserId(10L);
        target.setBuyerUserId(20L);
        target.setTradeAmount(BigDecimal.valueOf(30000L));
        target.setTradeStatus("TRDC0005");
        target.setAutoCompleteAt(now.minusSeconds(1));
        when(tradeMapper.findAutoCompletionTargetForUpdate(91L)).thenReturn(target);
        when(tradeMapper.completeExpiredConfirmation(91L, now, "SYSTEM")).thenReturn(1);
        when(settlementService.createPending(91L, 10L, 30000L)).thenReturn(501L);

        boolean completed = tradeService.completeExpiredConfirmation(91L, now);

        assertThat(completed).isTrue();
        verify(tradeMapper).insertStatusHistory(
                91L,
                "TRDC0006",
                "상대방 확인 기한이 지나 자동으로 거래가 완료되었습니다.");
        verify(settlementService).createPending(91L, 10L, 30000L);
        verify(settlementService).completeAutomatically(501L);
        verify(chatService).closeOfflineTradeChatRoom(91L);
        verify(notificationService).notifyTradeComplete(20L, 91L, true);
        verify(notificationService).notifyTradeComplete(10L, 91L, true);
    }

    @Test
    void ignoresConfirmationThatIsNotExpiredAfterLocking() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 12, 0);
        TradeAutoCompletionTarget target = new TradeAutoCompletionTarget();
        target.setTradeStatus("TRDC0005");
        target.setAutoCompleteAt(now.plusSeconds(1));
        when(tradeMapper.findAutoCompletionTargetForUpdate(91L)).thenReturn(target);

        boolean completed = tradeService.completeExpiredConfirmation(91L, now);

        assertThat(completed).isFalse();
        verifyNoInteractions(notificationService);
    }

    @Test
    void approvesSellerCancellationAndRecordsTradeStatusHistory() {
        TradeCancellationTarget target = new TradeCancellationTarget();
        target.setTradeId(91L);
        target.setSellerUserId(10L);
        target.setBuyerUserId(20L);
        target.setBidSn(501L);
        target.setTradeStatus("TRDC0004");
        SellerCancellationDecisionCommand command = new SellerCancellationDecisionCommand(
                91L,
                SellerCancellationDecision.APPROVED,
                "판매자 취소 요청을 승인합니다.",
                "admin-1",
                "request-1");
        when(tradeMapper.findMaterialTradeForCancellationForUpdate(91L)).thenReturn(target);
        when(tradeMapper.cancelMaterialTrade(91L, "admin-1")).thenReturn(1);

        tradeService.decide(command);

        verify(tradeMapper).cancelMaterialTrade(91L, "admin-1");
        verify(tradeMapper).insertStatusHistory(
                91L,
                "TRDC0008",
                "판매자 취소 요청을 승인합니다.");
        verify(pointService).refundEscrow(
                20L,
                91L,
                nct.common.domain.RefType.BID,
                501L,
                "관리자 판매자 취소 승인: 판매자 취소 요청을 승인합니다.");
        verify(notificationService).notifyTradeCancelled(20L, 91L, true);
        verify(notificationService).notifyTradeCancelled(10L, 91L, false);
    }

    @Test
    void rejectsSellerCancellationWithoutChangingTrade() {
        SellerCancellationDecisionCommand command = new SellerCancellationDecisionCommand(
                91L,
                SellerCancellationDecision.REJECTED,
                "취소 사유가 충분하지 않습니다.",
                "admin-1",
                "request-1");

        tradeService.decide(command);

        verifyNoInteractions(tradeMapper);
    }

    @Test
    void preventsSellerCancellationAfterTradeCompletion() {
        TradeCancellationTarget target = new TradeCancellationTarget();
        target.setTradeId(91L);
        target.setBidSn(501L);
        target.setTradeStatus("TRDC0006");
        SellerCancellationDecisionCommand command = new SellerCancellationDecisionCommand(
                91L,
                SellerCancellationDecision.APPROVED,
                "판매자 취소 요청을 승인합니다.",
                "admin-1",
                "request-1");
        when(tradeMapper.findMaterialTradeForCancellationForUpdate(91L)).thenReturn(target);

        assertThatThrownBy(() -> tradeService.decide(command))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_PROCESSED);
        verify(tradeMapper, never()).cancelMaterialTrade(anyLong(), any());
        verify(tradeMapper, never()).insertStatusHistory(anyLong(), any(), any());
    }

    private TradeDeliverySubmitTarget deliveryTarget(String tradeStatus, Long deliveryId) {
        TradeDeliverySubmitTarget target = new TradeDeliverySubmitTarget();
        target.setTradeId(91L);
        target.setDeliveryId(deliveryId);
        target.setBuyerUserId(20L);
        target.setTradeStatus(tradeStatus);
        return target;
    }

    private TradeDeliveryProofSubmitRequest deliveryProofRequest(List<Long> fileIds) {
        TradeDeliveryProofSubmitRequest request = new TradeDeliveryProofSubmitRequest();
        request.setDeliveryMessage("포장 후 발송했습니다.");
        request.setFileIds(fileIds);
        return request;
    }

    private TradeDetailResponse deliveryDetail(long tradeId, long deliveryId) {
        TradeDetailResponse detail = new TradeDetailResponse();
        detail.setTradeId(tradeId);
        detail.setDeliveryId(deliveryId);
        return detail;
    }

    private FileMeta deliveryFile(long fileId) {
        return FileMeta.builder()
                .flSn(fileId)
                .flPath("/api/attachment/delivery/20260721/proof.jpg")
                .build();
    }
}
