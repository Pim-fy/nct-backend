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

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private SettlementMapper settlementMapper;

    @Mock
    private PointService pointService;

    @Mock
    private NotificationService notificationService;

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
    void adminCompleteCreditsSettleablePointOnceWithAdminAuditActor() {
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

        settlementService.complete(501L, 700L);

        verify(pointService).creditSettleable(
                10L,
                30_000L,
                RefType.TRADE,
                91L,
                "정산 완료 (정산번호 501)");
        verify(notificationService).notifySettlement(
                10L,
                "정산 완료",
                "30,000P가 정산 가능 포인트로 적립되었습니다.",
                91L);
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

        assertThatThrownBy(() -> settlementService.complete(501L, 700L))
                .isInstanceOf(SettlementException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SETTLEMENT_INVALID_STATUS);

        verify(pointService, never()).creditSettleable(
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
        verify(pointService, never()).creditSettleable(
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
}
