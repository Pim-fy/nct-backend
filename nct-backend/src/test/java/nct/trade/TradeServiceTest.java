package nct.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import nct.file.service.FileStorageService;
import nct.file.domain.FileMeta;
import nct.trade.domain.Trade;
import nct.trade.dto.TradeAutoCompletionTarget;
import nct.trade.dto.MaterialTradeCreateCommand;
import nct.trade.dto.MaterialTradeCreateResult;
import nct.trade.dto.TradeConfirmationTarget;
import nct.trade.dto.TradeDetailResponse;
import nct.trade.dto.TradeDeliveryProofSubmitRequest;
import nct.trade.dto.TradeDeliverySubmitTarget;
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
    private TradeService tradeService;

    @BeforeEach
    void setUp() {
        tradeMapper = mock(TradeMapper.class);
        notificationService = mock(NotificationService.class);
        systemSettingMapper = mock(SystemSettingAdminMapper.class);
        fileStorageService = mock(FileStorageService.class);
        tradeService = new TradeService(
                tradeMapper,
                notificationService,
                systemSettingMapper,
                fileStorageService);
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
        when(tradeMapper.insertDeliverySnapshotFromBuyer(91L, 20L)).thenReturn(1);
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
        verify(tradeMapper).insertDeliverySnapshotFromBuyer(91L, 20L);
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
        verify(tradeMapper, never()).insertDeliverySnapshotFromBuyer(anyLong(), anyLong());
    }

    @Test
    void rejectsDeliveryTradeWhenWinningBuyerHasNoAddress() {
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
        when(tradeMapper.insertDeliverySnapshotFromBuyer(91L, 20L)).thenReturn(0);

        assertThatThrownBy(() -> tradeService.createOrGetMaterialTrade(command))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
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
        when(tradeMapper.findMyMaterialTradeDetail(91L, 10L)).thenReturn(detail);

        TradeDetailResponse result = tradeService.getMyMaterialTradeDetail(91L, 10L);

        assertThat(result).isSameAs(detail);
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
        when(tradeMapper.findMyMaterialTradeDetail(91L, 10L)).thenReturn(detail);

        TradeDetailResponse result = tradeService.saveMyOfflineSchedule(91L, 10L, request);

        verify(tradeMapper).upsertOfflineSchedule(
                91L,
                LocalDateTime.of(request.getMeetingDate(), request.getMeetingTime()),
                "합정역 8번 출구 앞",
                "서울 마포구 양화로 45");
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
    void buyerStartsCompletionConfirmationAndNotifiesSeller() {
        TradeConfirmationTarget target = new TradeConfirmationTarget();
        target.setTradeId(91L);
        target.setBuyerUserId(20L);
        target.setSellerUserId(10L);
        target.setTradeStatus("TRDC0004");
        TradeDetailResponse detail = new TradeDetailResponse();
        detail.setTradeId(91L);
        SystemSettingDetail setting = new SystemSettingDetail();
        setting.setTrdCfmnDays(5);

        when(tradeMapper.findBuyerTradeForConfirmationForUpdate(91L, 20L))
                .thenReturn(target);
        when(systemSettingMapper.selectOne()).thenReturn(setting);
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
    void rejectsCompletionRequestWhenAlreadyWaitingForConfirmation() {
        TradeConfirmationTarget target = new TradeConfirmationTarget();
        target.setTradeStatus("TRDC0005");
        when(tradeMapper.findBuyerTradeForConfirmationForUpdate(91L, 20L))
                .thenReturn(target);

        assertThatThrownBy(() -> tradeService.requestCompletionConfirmation(91L, 20L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_PROCESSED);
        verifyNoInteractions(systemSettingMapper, notificationService);
    }

    @Test
    void completesExpiredConfirmationAndNotifiesBothParties() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 12, 0);
        TradeAutoCompletionTarget target = new TradeAutoCompletionTarget();
        target.setTradeId(91L);
        target.setSellerUserId(10L);
        target.setBuyerUserId(20L);
        target.setTradeStatus("TRDC0005");
        target.setAutoCompleteAt(now.minusSeconds(1));
        when(tradeMapper.findAutoCompletionTargetForUpdate(91L)).thenReturn(target);
        when(tradeMapper.completeExpiredConfirmation(91L, now, "SYSTEM")).thenReturn(1);

        boolean completed = tradeService.completeExpiredConfirmation(91L, now);

        assertThat(completed).isTrue();
        verify(tradeMapper).insertStatusHistory(
                91L,
                "TRDC0006",
                "상대방 확인 기한이 지나 자동으로 거래가 완료되었습니다.");
        verify(notificationService).notify(
                20L,
                nct.notification.domain.NotificationType.TRADE,
                nct.notification.domain.NotificationDomain.TRADE,
                "거래 자동 완료",
                "상대방 확인 기한이 지나 거래가 자동으로 완료되었습니다.",
                nct.common.domain.RefType.TRADE,
                91L);
        verify(notificationService).notify(
                10L,
                nct.notification.domain.NotificationType.TRADE,
                nct.notification.domain.NotificationDomain.TRADE,
                "거래 자동 완료",
                "상대방 확인 기한이 지나 거래가 자동으로 완료되었습니다.",
                nct.common.domain.RefType.TRADE,
                91L);
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

    private TradeDeliverySubmitTarget deliveryTarget(String tradeStatus, Long deliveryId) {
        TradeDeliverySubmitTarget target = new TradeDeliverySubmitTarget();
        target.setTradeId(91L);
        target.setDeliveryId(deliveryId);
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
