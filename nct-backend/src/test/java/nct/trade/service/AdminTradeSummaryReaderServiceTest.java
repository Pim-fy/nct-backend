package nct.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import nct.trade.mapper.TradeMapper;

/** 담당자 7 · F-OPS-010: 전체 거래 집계가 거래 소유 Mapper를 사용하는지 검증합니다. */
class AdminTradeSummaryReaderServiceTest {

    @Test
    void countsAllTrades() {
        TradeMapper tradeMapper = mock(TradeMapper.class);
        when(tradeMapper.countAllTrades()).thenReturn(34L);
        AdminTradeSummaryReaderService service = new AdminTradeSummaryReaderService(tradeMapper);

        long result = service.countAllTrades();

        assertThat(result).isEqualTo(34L);
        verify(tradeMapper).countAllTrades();
    }
}
