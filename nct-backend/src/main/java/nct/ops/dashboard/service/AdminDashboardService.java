package nct.ops.dashboard.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.abuse.port.AdminActiveDisputeCountReader;
import nct.member.port.AdminMemberSummaryReader;
import nct.ops.dashboard.dto.AdminDashboardSummaryResponse;
import nct.ops.dashboard.mapper.AdminDashboardMapper;
import nct.ops.risk.mapper.RiskEventMapper;
import nct.point.service.PointExchangeService;
import nct.settlement.port.AdminIncompleteSettlementCountReader;
import nct.trade.port.AdminTradeSummaryReader;

/**
 * 담당자 7 · F-OPS-010: 관리자 운영 대시보드 집계 조회 서비스입니다.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final AdminMemberSummaryReader memberSummaryReader;
    private final AdminTradeSummaryReader tradeSummaryReader;
    private final AdminActiveDisputeCountReader activeDisputeCountReader;
    private final AdminIncompleteSettlementCountReader incompleteSettlementCountReader;
    private final AdminDashboardMapper adminDashboardMapper;
    private final RiskEventMapper riskEventMapper;
    private final PointExchangeService pointExchangeService;

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getSummary() {
        AdminDashboardSummaryResponse response = new AdminDashboardSummaryResponse();
        response.setActiveUserCount(memberSummaryReader.countActiveUsers());
        response.setTotalTradeCount(tradeSummaryReader.countAllTrades());
        response.setActiveDisputeCount(activeDisputeCountReader.countActiveDisputesForAdmin());
        response.setIncompleteSettlementCount(
                incompleteSettlementCountReader.countIncompleteSettlementsForAdmin());
        response.setPendingProviderApplicationCount(
                adminDashboardMapper.countPendingProviderApplications());
        response.setPendingReportCount(
                adminDashboardMapper.countPendingReports());
        response.setPendingExchangeCount(
                pointExchangeService.countRequestedForAdmin());
        response.setUnprocessedRiskEventCount(
                riskEventMapper.countAdminRiskEvents(null, "N", null, null, null));
        return response;
    }
}
