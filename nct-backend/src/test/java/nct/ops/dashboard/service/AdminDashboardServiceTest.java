package nct.ops.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import nct.ops.dashboard.dto.AdminDashboardSummaryResponse;
import nct.ops.risk.mapper.RiskEventMapper;

/** 담당자 7 · F-OPS-010: 소유 집계만 반환하고 미계약 수치를 만들지 않는지 검증합니다. */
class AdminDashboardServiceTest {

    @Test
    void returnsOwnedRiskEventSummaryWithoutInventingOtherDomainCounts() {
        RiskEventMapper mapper = mock(RiskEventMapper.class);
        when(mapper.countAdminRiskEvents(null, "N")).thenReturn(4L);

        AdminDashboardSummaryResponse result = new AdminDashboardService(mapper).getSummary();

        assertThat(result.getActiveUserCount()).isNull();
        assertThat(result.getTotalTradeCount()).isNull();
        assertThat(result.getActiveDisputeCount()).isNull();
        assertThat(result.getIncompleteSettlementCount()).isNull();
        assertThat(result.getUnprocessedRiskEventCount()).isEqualTo(4L);
        verify(mapper).countAdminRiskEvents(null, "N");
    }
}
