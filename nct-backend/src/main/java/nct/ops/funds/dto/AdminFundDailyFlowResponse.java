package nct.ops.funds.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminFundDailyFlowResponse {

    private LocalDate date;
    private long chargeAmount;
    private long exchangePaidAmount;
    private long commissionAmount;
    private long auctionTradeAmount;
    private long serviceTradeAmount;
}
