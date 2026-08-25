package nct.ops.funds.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminFundDashboardSummaryResponse {

    private LocalDateTime generatedAt;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private int periodDays;
    private long periodChargeAmount;
    private long periodExchangePaidAmount;
    private long periodCommissionAmount;
    private long periodAuctionTradeAmount;
    private long periodServiceTradeAmount;
    private long activeEscrowAmount;
    private long heldSettlementAmount;
    private long heldSettlementCount;
    private long pendingExchangeAmount;
    private long pendingExchangeCount;
    private long availablePointBalance;
    private long holdPointBalance;
    private long attentionHoldAmount;
    private long attentionHoldCount;
    private long settleablePointBalance;
    private List<AdminFundDailyFlowResponse> dailyFlows;
}
