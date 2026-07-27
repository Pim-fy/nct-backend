package nct.auction.dto;

import java.math.BigDecimal;

import lombok.Getter;

@Getter
public class AuctionBidCreateCommand {

    private Long bidId;
    private final Long auctionId;
    private final Long userId;
    private final BigDecimal bidAmount;
    private final String statusCode;
    private final String actor;

    public AuctionBidCreateCommand(
            Long auctionId,
            Long userId,
            BigDecimal bidAmount,
            String statusCode,
            String actor) {
        this.auctionId = auctionId;
        this.userId = userId;
        this.bidAmount = bidAmount;
        this.statusCode = statusCode;
        this.actor = actor;
    }
}
