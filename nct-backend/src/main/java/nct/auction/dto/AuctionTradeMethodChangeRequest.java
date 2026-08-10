package nct.auction.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuctionTradeMethodChangeRequest {

    private String tradeMethod;
    private Long deliveryAddressId;
}
