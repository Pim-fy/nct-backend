package nct.ops.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import nct.abuse.port.AdminActiveDisputeCountReader;
import nct.member.port.AdminMemberSummaryReader;
import nct.ops.dashboard.mapper.AdminDashboardMapper;
import nct.ops.risk.mapper.RiskEventMapper;
import nct.point.service.PointExchangeService;
import nct.settlement.port.AdminIncompleteSettlementCountReader;
import nct.trade.port.AdminTradeSummaryReader;

/** 담당자 7 · F-OPS-010: 대시보드가 실제 조치 필요 건수를 반환하는지 검증합니다. */
class AdminDashboardServiceTest {

    @Test
    void returnsActionRequiredCounts() {
        AdminMemberSummaryReader memberSummaryReader = mock(AdminMemberSummaryReader.class);
        AdminTradeSummaryReader tradeSummaryReader = mock(AdminTradeSummaryReader.class);
        AdminActiveDisputeCountReader activeDisputeCountReader =
                mock(AdminActiveDisputeCountReader.class);
        AdminIncompleteSettlementCountReader incompleteSettlementCountReader =
                mock(AdminIncompleteSettlementCountReader.class);
        AdminDashboardMapper adminDashboardMapper = mock(AdminDashboardMapper.class);
        RiskEventMapper riskEventMapper = mock(RiskEventMapper.class);
        PointExchangeService pointExchangeService = mock(PointExchangeService.class);
        when(memberSummaryReader.countActiveUsers()).thenReturn(12L);
        when(tradeSummaryReader.countAllTrades()).thenReturn(34L);
        when(activeDisputeCountReader.countActiveDisputesForAdmin()).thenReturn(6L);
        when(incompleteSettlementCountReader.countIncompleteSettlementsForAdmin()).thenReturn(7L);
        when(adminDashboardMapper.countPendingProviderApplications()).thenReturn(4L);
        when(adminDashboardMapper.countPendingReports()).thenReturn(5L);
        when(pointExchangeService.countRequestedForAdmin()).thenReturn(2L);
        when(riskEventMapper.countAdminRiskEvents(null, "N", null, null, null)).thenReturn(3L);

        AdminDashboardService service = new AdminDashboardService(
                memberSummaryReader,
                tradeSummaryReader,
                activeDisputeCountReader,
                incompleteSettlementCountReader,
                adminDashboardMapper,
                riskEventMapper,
                pointExchangeService);

        var response = service.getSummary();

        assertThat(response.getPendingProviderApplicationCount()).isEqualTo(4L);
        assertThat(response.getPendingReportCount()).isEqualTo(5L);
        assertThat(response.getPendingExchangeCount()).isEqualTo(2L);
        assertThat(response.getUnprocessedRiskEventCount()).isEqualTo(3L);
        assertThat(response.getActiveUserCount()).isEqualTo(12L);
        assertThat(response.getTotalTradeCount()).isEqualTo(34L);
        assertThat(response.getActiveDisputeCount()).isEqualTo(6L);
        assertThat(response.getIncompleteSettlementCount()).isEqualTo(7L);
    }

    /** 담당자 7 · ISSUE-T7-004: 대상 데이터가 없을 때도 null 대신 숫자 0을 반환합니다. */
    @Test
    void returnsZeroForEmptyDisputeAndSettlementCounts() {
        AdminActiveDisputeCountReader activeDisputeCountReader =
                mock(AdminActiveDisputeCountReader.class);
        AdminIncompleteSettlementCountReader incompleteSettlementCountReader =
                mock(AdminIncompleteSettlementCountReader.class);
        AdminDashboardService service = new AdminDashboardService(
                mock(AdminMemberSummaryReader.class),
                mock(AdminTradeSummaryReader.class),
                activeDisputeCountReader,
                incompleteSettlementCountReader,
                mock(AdminDashboardMapper.class),
                mock(RiskEventMapper.class),
                mock(PointExchangeService.class));

        var response = service.getSummary();

        assertThat(response.getActiveDisputeCount()).isZero();
        assertThat(response.getIncompleteSettlementCount()).isZero();
    }
}
