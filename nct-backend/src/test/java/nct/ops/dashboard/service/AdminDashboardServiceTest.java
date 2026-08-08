package nct.ops.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import nct.ops.dashboard.mapper.AdminDashboardMapper;
import nct.ops.risk.mapper.RiskEventMapper;
import nct.point.service.PointExchangeService;

/** 담당자 7 · F-OPS-010: 대시보드가 실제 조치 필요 건수를 반환하는지 검증합니다. */
class AdminDashboardServiceTest {

    @Test
    void returnsActionRequiredCounts() {
        AdminDashboardMapper adminDashboardMapper = mock(AdminDashboardMapper.class);
        RiskEventMapper riskEventMapper = mock(RiskEventMapper.class);
        PointExchangeService pointExchangeService = mock(PointExchangeService.class);
        when(adminDashboardMapper.countPendingProviderApplications()).thenReturn(4L);
        when(adminDashboardMapper.countPendingReports()).thenReturn(5L);
        when(pointExchangeService.countRequestedForAdmin()).thenReturn(2L);
        when(riskEventMapper.countAdminRiskEvents(null, "N", null, null, null)).thenReturn(3L);

        AdminDashboardService service = new AdminDashboardService(
                adminDashboardMapper,
                riskEventMapper,
                pointExchangeService);

        var response = service.getSummary();

        assertThat(response.getPendingProviderApplicationCount()).isEqualTo(4L);
        assertThat(response.getPendingReportCount()).isEqualTo(5L);
        assertThat(response.getPendingExchangeCount()).isEqualTo(2L);
        assertThat(response.getUnprocessedRiskEventCount()).isEqualTo(3L);
        assertThat(response.getActiveUserCount()).isNull();
        assertThat(response.getTotalTradeCount()).isNull();
        assertThat(response.getActiveDisputeCount()).isNull();
        assertThat(response.getIncompleteSettlementCount()).isNull();
    }
}
