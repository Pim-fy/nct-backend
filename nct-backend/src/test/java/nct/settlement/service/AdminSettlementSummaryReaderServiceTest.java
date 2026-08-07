package nct.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import nct.global.exception.CustomException;
import nct.settlement.dto.AdminSettlementSummary;
import nct.settlement.mapper.SettlementMapper;

/** 담당자 7 · F-OPS-021: 거래별 정산 요약의 허용 상태와 단일 행 계약을 검증합니다. */
class AdminSettlementSummaryReaderServiceTest {

    @Test
    void returnsSupportedSettlementStatus() {
        SettlementMapper mapper = mock(SettlementMapper.class);
        AdminSettlementSummary summary = summary(100L, 200L, "STLC0002");
        when(mapper.findAdminSummariesByTradeIds(List.of(100L))).thenReturn(List.of(summary));
        var service = new AdminSettlementSummaryReaderService(mapper);

        assertThat(service.findSummaries(List.of(100L))).containsEntry(100L, summary);
    }

    @Test
    void rejectsDuplicateSettlementForTrade() {
        SettlementMapper mapper = mock(SettlementMapper.class);
        when(mapper.findAdminSummariesByTradeIds(List.of(100L))).thenReturn(List.of(
                summary(100L, 200L, "STLC0001"),
                summary(100L, 201L, "STLC0002")));
        var service = new AdminSettlementSummaryReaderService(mapper);

        assertThatThrownBy(() -> service.findSummaries(List.of(100L)))
                .isInstanceOf(CustomException.class);
    }

    private AdminSettlementSummary summary(long tradeId, long settlementId, String statusCode) {
        AdminSettlementSummary summary = new AdminSettlementSummary();
        summary.setTradeId(tradeId);
        summary.setSettlementId(settlementId);
        summary.setStatusCode(statusCode);
        return summary;
    }
}
