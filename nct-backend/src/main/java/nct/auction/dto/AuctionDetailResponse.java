package nct.auction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuctionDetailResponse {

    private Long auctionId;
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
    private List<AuctionImageItem> images = List.of();
    private List<AuctionBidItem> bids = List.of();
}
