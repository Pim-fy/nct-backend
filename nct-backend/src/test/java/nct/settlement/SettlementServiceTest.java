package nct.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import nct.common.domain.RefType;
import nct.global.exception.ErrorCode;
import nct.notification.service.NotificationService;
import nct.point.service.PointService;
import nct.settlement.domain.Settlement;
import nct.settlement.domain.SettlementStatus;
import nct.settlement.exception.SettlementException;
import nct.settlement.mapper.SettlementMapper;
import nct.settlement.service.SettlementService;
import nct.trade.dto.TradeSettlementReference;
import nct.trade.port.TradeSettlementReferenceReader;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private SettlementMapper settlementMapper;

    @Mock
    private PointService pointService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private TradeSettlementReferenceReader tradeSettlementReferenceReader;

    @InjectMocks
    private SettlementService settlementService;

    @Test
    void createPendingCreatesOneSettlementAndReturnsItsId() {
        doAnswer(invocation -> {
            Settlement settlement = invocation.getArgument(0);
            settlement.setStlmSn(501L);
            return 1;
        }).when(settlementMapper).insert(any(Settlement.class));

        long settlementId = settlementService.createPending(91L, 10L, 30_000L);

        assertThat(settlementId).isEqualTo(501L);
        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementMapper).insert(captor.capture());
        assertThat(captor.getValue().getTrdSn()).isEqualTo(91L);
        assertThat(captor.getValue().getUsrSn()).isEqualTo(10L);
        assertThat(captor.getValue().getStlmAmt()).isEqualTo(30_000L);
        assertThat(captor.getValue().getStlmStatusCd())
                .isEqualTo(SettlementStatus.PENDING.getCode());
        verify(notificationService).notifySettlement(
                10L,
                "정산 대기",
                "거래대금 30,000P가 정산 대기 상태로 전환되었습니다.",
                91L);
    }

    @Test
    void createPendingReturnsExistingSettlementWhenTradeIsRetried() {
        doThrow(new DuplicateKeyException("UK_SETTLEMENT_TRD"))
                .when(settlementMapper).insert(any(Settlement.class));
        when(settlementMapper.selectByTradeForUpdate(91L))
                .thenReturn(settlement(501L, 91L, 10L, 30_000L, SettlementStatus.PENDING));

        long settlementId = settlementService.createPending(91L, 10L, 30_000L);

        assertThat(settlementId).isEqualTo(501L);
        verify(notificationService, never())
                .notifySettlement(anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void createPendingRejectsRetryWithDifferentSettlementData() {
        doThrow(new DuplicateKeyException("UK_SETTLEMENT_TRD"))
                .when(settlementMapper).insert(any(Settlement.class));
        when(settlementMapper.selectByTradeForUpdate(91L))
                .thenReturn(settlement(501L, 91L, 10L, 30_000L, SettlementStatus.PENDING));

        assertThatThrownBy(() -> settlementService.createPending(91L, 11L, 30_000L))
                .isInstanceOf(SettlementException.class)
                .hasMessageContaining("기존 정산 정보");
    }

    @Test
    void getSettlementByTradeReturnsPublicTradeLookupResult() {
        Settlement expected = settlement(
                501L,
                91L,
                10L,
                30_000L,
                SettlementStatus.PENDING);
        when(settlementMapper.selectByTrade(91L)).thenReturn(expected);

        assertThat(settlementService.getSettlementByTrade(91L)).isSameAs(expected);
    }

    @Test
    void getSettlementByTradeRejectsMissingSettlement() {
        when(settlementMapper.selectByTrade(91L)).thenReturn(null);

        assertThatThrownBy(() -> settlementService.getSettlementByTrade(91L))
                .isInstanceOf(SettlementException.class)
                .hasMessageContaining("연결된 정산 건이 없습니다");
    }

    @Test
    void adminCompleteCreditsMaterialTradeEscrowByBidReference() {
        Settlement pending = settlement(
                501L,
                91L,
                10L,
                30_000L,
                SettlementStatus.PENDING);
        when(settlementMapper.selectForUpdate(501L)).thenReturn(pending);
        when(settlementMapper.updateStatus(
                501L,
                SettlementStatus.COMPLETED.getCode(),
                "700"))
                .thenReturn(1);
        when(tradeSettlementReferenceReader.getByTradeSn(91L))
                .thenReturn(tradeReference(91L, "TRDC0001", 801L));
        when(pointService.creditEscrowToSettleable(
                10L,
                91L,
                RefType.BID,
                801L,
                "정산 완료 (정산번호 501)"))
                .thenReturn(30_000L);

        settlementService.complete(501L, 700L);

        verify(pointService).creditEscrowToSettleable(
                10L,
                91L,
                RefType.BID,
                801L,
                "정산 완료 (정산번호 501)");
        verify(notificationService).notifySettlement(
                10L,
                "정산 완료",
                "30,000P가 정산 가능 포인트로 적립되었습니다.",
                91L);
    }

    @Test
    void adminCompleteCreditsServiceTradeEscrowByTradeReference() {
        Settlement pending = settlement(
                501L,
                91L,
                10L,
                30_000L,
                SettlementStatus.PENDING);
        when(settlementMapper.selectForUpdate(501L)).thenReturn(pending);
        when(settlementMapper.updateStatus(
                501L,
                SettlementStatus.COMPLETED.getCode(),
                "700"))
                .thenReturn(1);
        when(tradeSettlementReferenceReader.getByTradeSn(91L))
                .thenReturn(tradeReference(91L, "TRDC0002", null));
        when(pointService.creditEscrowToSettleable(
                10L,
                91L,
                RefType.TRADE,
                91L,
                "정산 완료 (정산번호 501)"))
                .thenReturn(30_000L);

        settlementService.complete(501L, 700L);

        verify(pointService).creditEscrowToSettleable(
                10L,
                91L,
                RefType.TRADE,
                91L,
                "정산 완료 (정산번호 501)");
    }

    @Test
    void completeRejectsMaterialTradeWithoutBidReference() {
        Settlement pending = settlement(
                501L,
                91L,
                10L,
                30_000L,
                SettlementStatus.PENDING);
        when(settlementMapper.selectForUpdate(501L)).thenReturn(pending);
        when(tradeSettlementReferenceReader.getByTradeSn(91L))
                .thenReturn(tradeReference(91L, "TRDC0001", null));

        assertThatThrownBy(() -> settlementService.complete(501L, 700L))
                .isInstanceOf(SettlementException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        verify(settlementMapper, never()).updateStatus(anyLong(), anyString(), anyString());
        verify(pointService, never()).creditEscrowToSettleable(
                anyLong(), anyLong(), any(), anyLong(), anyString());
    }

    @Test
    void completeRejectsEscrowAmountMismatch() {
        Settlement pending = settlement(
                501L,
                91L,
                10L,
                30_000L,
                SettlementStatus.PENDING);
        when(settlementMapper.selectForUpdate(501L)).thenReturn(pending);
        when(settlementMapper.updateStatus(
                501L,
                SettlementStatus.COMPLETED.getCode(),
                "700"))
                .thenReturn(1);
        when(tradeSettlementReferenceReader.getByTradeSn(91L))
                .thenReturn(tradeReference(91L, "TRDC0002", null));
        when(pointService.creditEscrowToSettleable(
                10L,
                91L,
                RefType.TRADE,
                91L,
                "정산 완료 (정산번호 501)"))
                .thenReturn(29_000L);

        assertThatThrownBy(() -> settlementService.complete(501L, 700L))
                .isInstanceOf(SettlementException.class)
                .hasMessageContaining("정산 금액과 보관금 잔액");

        verify(notificationService, never())
                .notifySettlement(anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void completeDoesNotCreditPointWhenStatusUpdateFails() {
        Settlement pending = settlement(
                501L,
                91L,
                10L,
                30_000L,
                SettlementStatus.PENDING);
        when(settlementMapper.selectForUpdate(501L)).thenReturn(pending);
        when(settlementMapper.updateStatus(
                501L,
                SettlementStatus.COMPLETED.getCode(),
                "700"))
                .thenReturn(0);
        when(tradeSettlementReferenceReader.getByTradeSn(91L))
                .thenReturn(tradeReference(91L, "TRDC0002", null));

        assertThatThrownBy(() -> settlementService.complete(501L, 700L))
                .isInstanceOf(SettlementException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_INVALID_STATUS);

        verify(pointService, never()).creditEscrowToSettleable(
                anyLong(), anyLong(), any(), anyLong(), anyString());
    }

    @Test
    void completeRejectsAlreadyCompletedSettlement() {
        when(settlementMapper.selectForUpdate(501L)).thenReturn(settlement(
                501L,
                91L,
                10L,
                30_000L,
                SettlementStatus.COMPLETED));

        assertThatThrownBy(() -> settlementService.complete(501L, 700L))
                .isInstanceOf(SettlementException.class)
                .hasMessageContaining("완료 처리할 수 없는 상태");

        verify(settlementMapper, never()).updateStatus(anyLong(), anyString(), anyString());
        verify(pointService, never()).creditEscrowToSettleable(
                anyLong(), anyLong(), any(), anyLong(), anyString());
    }

    @Test
    void holdUpByTradeReturnsFalseWhenSettlementDoesNotExist() {
        when(settlementMapper.selectByTradeForUpdate(91L)).thenReturn(null);

        boolean held = settlementService.holdUpByTradeIfPending(91L, "거래 문제 접수");

        assertThat(held).isFalse();
        verify(settlementMapper, never()).updateStatus(anyLong(), anyString(), anyString());
        verify(notificationService, never())
                .notifySettlement(anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void holdUpByTradeChangesPendingSettlementToOnHold() {
        Settlement pending = settlement(
                501L,
                91L,
                10L,
                30_000L,
                SettlementStatus.PENDING);
        when(settlementMapper.selectByTradeForUpdate(91L)).thenReturn(pending);
        when(settlementMapper.updateStatus(
                501L,
                SettlementStatus.ON_HOLD.getCode(),
                "SYSTEM"))
                .thenReturn(1);

        boolean held = settlementService.holdUpByTradeIfPending(91L, "거래 문제 접수");

        assertThat(held).isTrue();
        verify(notificationService).notifySettlement(
                10L,
                "정산 보류",
                "거래대금 30,000P의 정산이 보류되었습니다. 사유: 거래 문제 접수",
                91L);
    }

    @Test
    void holdUpByTradeIsIdempotentWhenSettlementIsAlreadyOnHold() {
        when(settlementMapper.selectByTradeForUpdate(91L)).thenReturn(settlement(
                501L,
                91L,
                10L,
                30_000L,
                SettlementStatus.ON_HOLD));

        boolean held = settlementService.holdUpByTradeIfPending(91L, "거래 문제 재접수");

        assertThat(held).isFalse();
        verify(settlementMapper, never()).updateStatus(anyLong(), anyString(), anyString());
        verify(notificationService, never())
                .notifySettlement(anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void holdUpByTradeRejectsCompletedSettlement() {
        when(settlementMapper.selectByTradeForUpdate(91L)).thenReturn(settlement(
                501L,
                91L,
                10L,
                30_000L,
                SettlementStatus.COMPLETED));

        assertThatThrownBy(() -> settlementService.holdUpByTradeIfPending(91L, "완료 후 분쟁"))
                .isInstanceOf(SettlementException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_INVALID_STATUS);

        verify(settlementMapper, never()).updateStatus(anyLong(), anyString(), anyString());
    }

    @Test
    void automaticCompleteRejectsOnHoldSettlement() {
        when(settlementMapper.selectForUpdate(501L)).thenReturn(settlement(
                501L,
                91L,
                10L,
                30_000L,
                SettlementStatus.ON_HOLD));

        assertThatThrownBy(() -> settlementService.completeAutomatically(501L))
                .isInstanceOf(SettlementException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_INVALID_STATUS);

        verify(settlementMapper, never()).updateStatus(anyLong(), anyString(), anyString());
        verify(pointService, never()).creditEscrowToSettleable(
                anyLong(), anyLong(), any(), anyLong(), anyString());
    }

    private Settlement settlement(
            long settlementId,
            long tradeId,
            long userId,
            long amount,
            SettlementStatus status) {
        Settlement settlement = new Settlement();
        settlement.setStlmSn(settlementId);
        settlement.setTrdSn(tradeId);
        settlement.setUsrSn(userId);
        settlement.setStlmAmt(amount);
        settlement.setStlmStatusCd(status.getCode());
        return settlement;
    }

    private TradeSettlementReference tradeReference(
            long tradeSn,
            String tradeTypeCode,
            Long bidSn) {
        TradeSettlementReference reference = new TradeSettlementReference();
        reference.setTradeSn(tradeSn);
        reference.setTradeTypeCode(tradeTypeCode);
        reference.setBidSn(bidSn);
        return reference;
    }
}
