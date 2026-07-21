package nct.auction.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuctionStatusSummaryResponse {

    private Long prdSn;
    private Long aucSn;
    private String aucStatusCd;
}
