package nct.auction.dto;

import lombok.Getter;

@Getter
public class AuctionCancelRequestCreateCommand {

    private Long aucCnlReqSn;
    private final Long aucSn;
    private final Long requesterUsrSn;
    private final String reason;
    private final String prevAucStatusCd;
    private final String actor;

    public AuctionCancelRequestCreateCommand(
            Long aucSn,
            Long requesterUsrSn,
            String reason,
            String prevAucStatusCd,
            String actor) {
        this.aucSn = aucSn;
        this.requesterUsrSn = requesterUsrSn;
        this.reason = reason;
        this.prevAucStatusCd = prevAucStatusCd;
        this.actor = actor;
    }
}
