package nct.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import nct.abuse.port.ActiveAbuseReportReferenceReader;
import nct.trade.mapper.TradeMapper;

/** 담당자 7 · F-OPS-007: 현재 신고를 제외한 상위 신고와 기존 하위 분쟁을 함께 판정합니다. */
class TradeIncidentStatusQueryServiceTest {

    @Test
    void currentReportIsExcludedButAnotherActiveReportBlocksCancellation() {
        ActiveAbuseReportReferenceReader reportReader =
                mock(ActiveAbuseReportReferenceReader.class);
        TradeMapper tradeMapper = mock(TradeMapper.class);
        TradeIncidentStatusQueryService service =
                new TradeIncidentStatusQueryService(reportReader, tradeMapper);
        when(reportReader.hasOtherActiveReportLinkedToTrade(81L, 501L)).thenReturn(true);

        assertThat(service.hasOtherOpenIncident(81L, 501L)).isTrue();

        verify(reportReader).hasOtherActiveReportLinkedToTrade(81L, 501L);
        verifyNoInteractions(tradeMapper);
    }

    @Test
    void unlinkedLegacyDisputeStillBlocksCancellation() {
        ActiveAbuseReportReferenceReader reportReader =
                mock(ActiveAbuseReportReferenceReader.class);
        TradeMapper tradeMapper = mock(TradeMapper.class);
        TradeIncidentStatusQueryService service =
                new TradeIncidentStatusQueryService(reportReader, tradeMapper);
        when(reportReader.hasOtherActiveReportLinkedToTrade(81L, 501L)).thenReturn(false);
        when(tradeMapper.hasOtherOpenTradeDispute(81L, 501L)).thenReturn(true);

        assertThat(service.hasOtherOpenIncident(81L, 501L)).isTrue();

        verify(tradeMapper).hasOtherOpenTradeDispute(81L, 501L);
    }
}
