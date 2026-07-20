package nct.auction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuctionBidTarget {

    private Long auctionId;
    private Long sellerId;
    private Long currentHighestBidId;
    private Long currentHighestBidderId;
    private BigDecimal currentPrice;
    private BigDecimal bidUnitPrice;
    private BigDecimal instantBuyPrice;
    private String auctionStatusCode;
    private LocalDateTime endDateTime;
}
