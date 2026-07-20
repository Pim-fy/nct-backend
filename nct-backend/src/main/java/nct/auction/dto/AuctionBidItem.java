package nct.auction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuctionBidItem {

    private Long bidId;
    private BigDecimal bidPrice;
    private String bidStatusCode;
    private String bidStatusName;
    private String bidderName;
    private LocalDateTime bidDateTime;
}
