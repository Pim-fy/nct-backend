package nct.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import nct.abuse.port.ReportTargetHoldResult;
import nct.abuse.port.ReportTargetRestoreCommand;
import nct.settlement.service.SettlementService;
import nct.trade.dto.TradeDisputeTarget;
import nct.trade.mapper.TradeMapper;

/** 담당자 7 · F-OPS-007/F-OPS-008: 거래 단건 보류와 정산 복구 경계를 검증합니다. */
class TradeReportTargetHoldServiceTest {

    private TradeMapper tradeMapper;
    private SettlementService settlementService;
    private ObjectProvider<SettlementService> settlementServiceProvider;
    private TradeReportTargetHoldService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        tradeMapper = mock(TradeMapper.class);
        settlementService = mock(SettlementService.class);
        settlementServiceProvider = mock(ObjectProvider.class);
        when(settlementServiceProvider.getObject()).thenReturn(settlementService);
        service = new TradeReportTargetHoldService(tradeMapper, settlementServiceProvider);
    }

    @Test
    void pausesOnlyReferencedTradeAndPendingSettlement() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 17, 10, 0);
        TradeDisputeTarget target = target(401L, "TRDC0005", now, now.plusHours(2));
        when(tradeMapper.findTradeReportTargetForUpdate(401L)).thenReturn(target);
        when(tradeMapper.holdTradeForReport(401L, "7")).thenReturn(1);
        when(settlementService.holdUpByTradeIfPending(401L, "신고 접수에 따른 거래 단건 보류"))
                .thenReturn(true);

        ReportTargetHoldResult result = service.pause(401L, "7");

        assertThat(result.changed()).isTrue();
        assertThat(result.remainingSeconds()).isEqualTo(7200L);
        assertThat(result.settlementHoldApplied()).isTrue();
        verify(tradeMapper).holdTradeForReport(401L, "7");
        verify(tradeMapper).insertStatusHistory(
                401L, "TRDC0007", "신고 접수에 따른 거래 단건 보류");
    }

    @Test
    void skipsCompletedTradeWithoutChangingSettlement() {
        TradeDisputeTarget target = target(401L, "TRDC0006", null, null);
        when(tradeMapper.findTradeReportTargetForUpdate(401L)).thenReturn(target);

        ReportTargetHoldResult result = service.pause(401L, "7");

        assertThat(result.changed()).isFalse();
        verify(tradeMapper, never()).holdTradeForReport(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
        verify(settlementService, never()).holdUpByTradeIfPending(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void restoresTradeAndOnlySettlementHeldByThisReport() {
        ReportTargetRestoreCommand command = new ReportTargetRestoreCommand(
                401L, "TRDC0005", null, 7200L, true, "7");
        when(tradeMapper.restoreTradeAfterMemberRestriction(
                401L, "TRDC0005", 7200L, "7")).thenReturn(1);

        assertThat(service.restore(command)).isTrue();

        verify(tradeMapper).insertStatusHistory(
                401L, "TRDC0005", "마지막 활성 신고 해소에 따른 거래 복구");
        verify(settlementService).resumeByTradeIfOnHold(401L, 7L);
    }

    private TradeDisputeTarget target(
            long tradeSn,
            String statusCode,
            LocalDateTime databaseNow,
            LocalDateTime autoCompleteAt) {
        TradeDisputeTarget target = new TradeDisputeTarget();
        target.setTradeSn(tradeSn);
        target.setTradeStatusCode(statusCode);
        target.setDatabaseNow(databaseNow);
        target.setAutoCompleteAt(autoCompleteAt);
        return target;
    }
}
