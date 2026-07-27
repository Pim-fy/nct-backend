package nct.auction.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuctionCancellationTarget {

    private Long aucSn;
    private Long sellerUsrSn;
    private String aucStatusCd;
}
