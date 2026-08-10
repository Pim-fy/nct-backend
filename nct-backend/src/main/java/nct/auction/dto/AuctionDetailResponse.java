package nct.auction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuctionDetailResponse {

    private Long auctionId;
    /** 즉시구매 성공 응답에서 생성된 구매자 거래 상세 이동에 사용합니다. */
    private Long tradeId;
    private Long productId;
    private String title;
    private String content;
    private String categoryName;
    private BigDecimal currentPrice;
    private BigDecimal startPrice;
    private BigDecimal instantBuyPrice;
    private BigDecimal bidUnitPrice;
    private String auctionStatusCode;
    private String auctionStatusName;
    private String tradeMethodCode;
    private String tradeMethodName;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private Integer bidCount;
    private Integer favoriteCount;
    private boolean favorite;
    private Integer viewCount;
    private Long sellerId;
    private String sellerName;
    private Double sellerRating;
    private Integer sellerReviewCount;
    @JsonIgnore
    private Long currentHighestBidId;
    @JsonIgnore
    private Long currentHighestBidderId;
    private boolean currentHighestBidder;
    private String myBidTradeMethodCode;
    private Long myBidDeliveryAddressId;
    private boolean hasBidHistory;
    private List<AuctionImageItem> images = List.of();
    private List<AuctionBidItem> bids = List.of();
    private List<AuctionProductUpdateItem> productUpdates = List.of();
}
