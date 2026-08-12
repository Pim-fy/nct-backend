package nct.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import nct.abuse.port.ActiveAbuseReportReferenceReader;

/** 담당자 7 · F-OPS-007: 현재 신고를 제외한 다른 활성 거래 신고를 판정합니다. */
class TradeIncidentStatusQueryServiceTest {

    @Test
    void currentReportIsExcludedButAnotherActiveReportBlocksCancellation() {
        ActiveAbuseReportReferenceReader reportReader =
                mock(ActiveAbuseReportReferenceReader.class);
        TradeIncidentStatusQueryService service =
                new TradeIncidentStatusQueryService(reportReader);
        when(reportReader.hasOtherActiveReportLinkedToTrade(81L, 501L)).thenReturn(true);

        assertThat(service.hasOtherOpenIncident(81L, 501L)).isTrue();

        verify(reportReader).hasOtherActiveReportLinkedToTrade(81L, 501L);
    }

    @Test
    void returnsFalseWhenNoOtherActiveTradeReportExists() {
        ActiveAbuseReportReferenceReader reportReader =
                mock(ActiveAbuseReportReferenceReader.class);
        TradeIncidentStatusQueryService service =
                new TradeIncidentStatusQueryService(reportReader);
        when(reportReader.hasOtherActiveReportLinkedToTrade(81L, 501L)).thenReturn(false);

        assertThat(service.hasOtherOpenIncident(81L, 501L)).isFalse();

        verify(reportReader).hasOtherActiveReportLinkedToTrade(81L, 501L);
    }
}
