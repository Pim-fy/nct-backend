package nct.trade;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import nct.setting.domain.SystemSettingDetail;
import nct.setting.mapper.SystemSettingAdminMapper;
import nct.trade.mapper.TradeMapper;
import nct.trade.scheduler.TradeAutoCompletionScheduler;
import nct.trade.service.TradeService;

class TradeAutoCompletionSchedulerTest {

    @Test
    void completesExpiredTradesOnlyWhenAutoCompletionIsEnabled() {
        SystemSettingAdminMapper settingMapper = mock(SystemSettingAdminMapper.class);
        TradeMapper tradeMapper = mock(TradeMapper.class);
        TradeService tradeService = mock(TradeService.class);
        SystemSettingDetail setting = new SystemSettingDetail();
        setting.setAutoCmplYn("Y");
        when(settingMapper.selectOne()).thenReturn(setting);
        when(tradeMapper.findExpiredAutoCompletionTradeIds(any(), eq(100)))
                .thenReturn(List.of(91L, 92L));
        TradeAutoCompletionScheduler scheduler = new TradeAutoCompletionScheduler(
                settingMapper,
                tradeMapper,
                tradeService,
                true);

        scheduler.completeExpiredTrades();

        verify(tradeService).completeExpiredConfirmation(eq(91L), any());
        verify(tradeService).completeExpiredConfirmation(eq(92L), any());
    }

    @Test
    void skipsBatchWhenAutoCompletionIsDisabled() {
        SystemSettingAdminMapper settingMapper = mock(SystemSettingAdminMapper.class);
        TradeMapper tradeMapper = mock(TradeMapper.class);
        TradeService tradeService = mock(TradeService.class);
        SystemSettingDetail setting = new SystemSettingDetail();
        setting.setAutoCmplYn("N");
        when(settingMapper.selectOne()).thenReturn(setting);
        TradeAutoCompletionScheduler scheduler = new TradeAutoCompletionScheduler(
                settingMapper,
                tradeMapper,
                tradeService,
                true);

        scheduler.completeExpiredTrades();

        verify(tradeMapper, never()).findExpiredAutoCompletionTradeIds(any(), anyInt());
        verifyNoInteractions(tradeService);
    }

    @Test
    void skipsBatchUntilDeploymentEnablesScheduler() {
        SystemSettingAdminMapper settingMapper = mock(SystemSettingAdminMapper.class);
        TradeMapper tradeMapper = mock(TradeMapper.class);
        TradeService tradeService = mock(TradeService.class);
        TradeAutoCompletionScheduler scheduler = new TradeAutoCompletionScheduler(
                settingMapper,
                tradeMapper,
                tradeService,
                false);

        scheduler.completeExpiredTrades();

        verifyNoInteractions(settingMapper, tradeMapper, tradeService);
    }
}
