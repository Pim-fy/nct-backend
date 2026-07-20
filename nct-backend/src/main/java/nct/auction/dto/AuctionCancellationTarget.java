package nct.auction.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuctionCancellationTarget {

    private Long auctionId;
    private Long productId;
    private Long sellerId;
    private String auctionStatusCode;
    private Long highestBidId;
    private Long highestBidderId;
}
