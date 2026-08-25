package nct.ops.funds.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminFundSnapshot {

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
}
