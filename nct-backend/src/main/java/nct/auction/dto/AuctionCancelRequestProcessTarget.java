package nct.auction.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuctionCancelRequestProcessTarget {

    private Long cancelRequestSn;
    private Long auctionId;
    private String previousAuctionStatusCode;
    private String approvalYn;
}
