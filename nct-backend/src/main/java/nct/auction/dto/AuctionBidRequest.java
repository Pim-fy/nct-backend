package nct.auction.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuctionBidRequest {

    private BigDecimal bidAmount;
    private String tradeMethod;
}
