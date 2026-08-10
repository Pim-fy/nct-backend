package nct.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import nct.global.exception.CustomException;
import nct.trade.dto.AdminServiceTradeSummary;
import nct.trade.mapper.TradeMapper;

/** 담당자 7 · F-OPS-021: 서비스 요청별 거래 요약의 중복·상태 계약을 검증합니다. */
class AdminServiceTradeSummaryReaderServiceTest {

    @Test
    void deduplicatesInputAndReturnsValidatedSummary() {
        TradeMapper mapper = mock(TradeMapper.class);
        AdminServiceTradeSummary summary = summary(10L, 100L, 1000L, "TRDC0003");
        when(mapper.findAdminServiceTradeSummaries(List.of(10L))).thenReturn(List.of(summary));
        var service = new AdminServiceTradeSummaryReaderService(mapper);

        var result = service.findSummaries(List.of(10L, 10L));

        assertThat(result).containsEntry(10L, summary);
        verify(mapper).findAdminServiceTradeSummaries(List.of(10L));
    }

    @Test
    void rejectsMultipleTradesForOneServiceRequest() {
        TradeMapper mapper = mock(TradeMapper.class);
        when(mapper.findAdminServiceTradeSummaries(List.of(10L))).thenReturn(List.of(
                summary(10L, 100L, 1000L, "TRDC0003"),
                summary(10L, 101L, 1001L, "TRDC0003")));
        var service = new AdminServiceTradeSummaryReaderService(mapper);

        assertThatThrownBy(() -> service.findSummaries(List.of(10L)))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void rejectsUnsupportedTradeStatus() {
        TradeMapper mapper = mock(TradeMapper.class);
        when(mapper.findAdminServiceTradeSummaries(List.of(10L))).thenReturn(List.of(
                summary(10L, 100L, 1000L, "TRDC9999")));
        var service = new AdminServiceTradeSummaryReaderService(mapper);

        assertThatThrownBy(() -> service.findSummaries(List.of(10L)))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void rejectsUnsupportedDisputeStatusCount() {
        TradeMapper mapper = mock(TradeMapper.class);
        AdminServiceTradeSummary summary = summary(10L, 100L, 1000L, "TRDC0003");
        summary.setUnsupportedDisputeCount(1);
        when(mapper.findAdminServiceTradeSummaries(List.of(10L))).thenReturn(List.of(summary));
        var service = new AdminServiceTradeSummaryReaderService(mapper);

        assertThatThrownBy(() -> service.findSummaries(List.of(10L)))
                .isInstanceOf(CustomException.class);
    }

    private AdminServiceTradeSummary summary(
            long serviceRequestId,
            long tradeId,
            long quoteId,
            String statusCode) {
        AdminServiceTradeSummary summary = new AdminServiceTradeSummary();
        summary.setServiceRequestId(serviceRequestId);
        summary.setTradeId(tradeId);
        summary.setQuoteId(quoteId);
        summary.setTradeStatusCode(statusCode);
        return summary;
    }
}
