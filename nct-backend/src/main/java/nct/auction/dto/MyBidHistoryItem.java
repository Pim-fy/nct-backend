package nct.auction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;
import nct.auction.constant.AuctionStatusCode;
import nct.auction.constant.BidStatusCode;

@Getter
@Setter
public class MyBidHistoryItem {

    private Long bidSn;
    private Long aucSn;
    private Long prdSn;
    private String auctionTitle;
    private String thumbnailPath;
    private BigDecimal bidAmount;
    private BigDecimal currentPrice;
    private String bidStatusCode;
    private String auctionStatusCode;
    private LocalDateTime bidDateTime;
    private LocalDateTime auctionEndDateTime;
    private Long sellerSn;
    private String sellerName;

    public String resolveDisplayStatus() {
        return getDisplayStatus();
    }

    @JsonProperty("displayStatus")
    public String getDisplayStatus() {
        if (BidStatusCode.OUTBID.equals(bidStatusCode)) {
            return "OUTBID";
        }
        if (BidStatusCode.CANCELED.equals(bidStatusCode)
                || BidStatusCode.EXCEPTION_CANCELED.equals(bidStatusCode)
                || AuctionStatusCode.CANCELED.equals(auctionStatusCode)
                || AuctionStatusCode.FAILED.equals(auctionStatusCode)) {
            return "CANCELED";
        }
        if (AuctionStatusCode.CANCEL_REQUESTED.equals(auctionStatusCode)) {
            return "CANCEL_REQUESTED";
        }
        if (BidStatusCode.HIGHEST.equals(bidStatusCode)
                && AuctionStatusCode.ACTIVE.equals(auctionStatusCode)) {
            return "HIGHEST";
        }
        if (BidStatusCode.HIGHEST.equals(bidStatusCode)
                && AuctionStatusCode.ENDED.equals(auctionStatusCode)) {
            return "WON";
        }
        return "UNKNOWN";
    }
}
