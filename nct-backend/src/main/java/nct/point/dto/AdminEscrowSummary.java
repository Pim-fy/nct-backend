package nct.point.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7 · F-OPS-021: 거래 참조별 보관금·정산 원장 합계입니다. */
@Getter
@Setter
@NoArgsConstructor
public class AdminEscrowSummary {
    private Long tradeId;
    private long escrowDebitedAmount;
    private long refundedAmount;
    private long escrowLedgerAmount;
    private long settledAmount;

    public long activeEscrowAmount() {
        return settledAmount > 0 ? 0 : Math.max(0, escrowDebitedAmount - refundedAmount);
    }
}
