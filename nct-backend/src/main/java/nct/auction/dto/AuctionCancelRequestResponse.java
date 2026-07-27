package nct.auction.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuctionCancelRequestResponse {

    private Long aucCnlReqSn;
    private Long aucSn;
    private String prevAucStatusCd;
    private String aucStatusCd;
}
