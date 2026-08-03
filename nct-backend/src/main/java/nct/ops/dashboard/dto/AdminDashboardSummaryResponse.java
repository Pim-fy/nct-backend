package nct.ops.dashboard.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 담당자 7 · F-OPS-010: 관리자 운영 대시보드의 읽기 전용 집계 응답입니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminDashboardSummaryResponse {

    private Long activeUserCount;
    private Long totalTradeCount;
    private Long activeDisputeCount;
    private Long incompleteSettlementCount;
    private Long pendingProviderApplicationCount;
    private Long pendingReportCount;
    private Long pendingExchangeCount;
    private Long unprocessedRiskEventCount;
}
