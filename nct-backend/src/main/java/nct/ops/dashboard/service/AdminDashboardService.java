package nct.ops.dashboard.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.ops.dashboard.dto.AdminDashboardSummaryResponse;
import nct.ops.risk.mapper.RiskEventMapper;

/**
 * 담당자 7 · F-OPS-010: 관리자 운영 대시보드 집계 조회 서비스입니다.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final RiskEventMapper riskEventMapper;

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getSummary() {
        AdminDashboardSummaryResponse response = new AdminDashboardSummaryResponse();
        response.setUnprocessedRiskEventCount(
                riskEventMapper.countAdminRiskEvents(null, "N"));
        return response;
    }
}
