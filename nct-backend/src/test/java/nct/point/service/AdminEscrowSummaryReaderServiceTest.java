package nct.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import nct.global.exception.CustomException;
import nct.point.dto.AdminEscrowSummary;
import nct.point.mapper.PointMapper;

/** 담당자 7 · F-OPS-021: 원장 합계로 현재 보관금과 기지급 정산을 구분합니다. */
class AdminEscrowSummaryReaderServiceTest {

    @Test
    void settledLedgerMakesActiveEscrowZero() {
        PointMapper mapper = mock(PointMapper.class);
        AdminEscrowSummary summary = new AdminEscrowSummary();
        summary.setTradeId(100L);
        summary.setEscrowDebitedAmount(30_000L);
        summary.setEscrowLedgerAmount(-30_000L);
        summary.setSettledAmount(30_000L);
        when(mapper.findAdminEscrowSummaries(List.of(100L))).thenReturn(List.of(summary));
        var service = new AdminEscrowSummaryReaderService(mapper);

        var result = service.findSummaries(List.of(100L)).get(100L);

        assertThat(result.activeEscrowAmount()).isZero();
        assertThat(result.getSettledAmount()).isEqualTo(30_000L);
    }

    @Test
    void rejectsPositiveEscrowLedgerNet() {
        PointMapper mapper = mock(PointMapper.class);
        AdminEscrowSummary summary = new AdminEscrowSummary();
        summary.setTradeId(100L);
        summary.setEscrowDebitedAmount(1L);
        summary.setRefundedAmount(2L);
        summary.setEscrowLedgerAmount(1L);
        when(mapper.findAdminEscrowSummaries(List.of(100L))).thenReturn(List.of(summary));
        var service = new AdminEscrowSummaryReaderService(mapper);

        assertThatThrownBy(() -> service.findSummaries(List.of(100L)))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void rejectsPartialSettlementLedger() {
        PointMapper mapper = mock(PointMapper.class);
        AdminEscrowSummary summary = new AdminEscrowSummary();
        summary.setTradeId(100L);
        summary.setEscrowDebitedAmount(30_000L);
        summary.setEscrowLedgerAmount(-30_000L);
        summary.setSettledAmount(1L);
        when(mapper.findAdminEscrowSummaries(List.of(100L))).thenReturn(List.of(summary));
        var service = new AdminEscrowSummaryReaderService(mapper);

        assertThatThrownBy(() -> service.findSummaries(List.of(100L)))
                .isInstanceOf(CustomException.class);
    }
}
