package nct.favorite.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FavoriteAuctionItem {

    private Long auctionId;
    private Long productId;
    private String title;
    private String categoryName;
    private BigDecimal currentPrice;
    private BigDecimal startPrice;
    private BigDecimal instantBuyPrice;
    private String auctionStatusCode;
    private String auctionStatusName;
    private String tradeMethodCode;
    private String tradeMethodName;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private Integer bidCount;
    private Integer favoriteCount;
    private Integer viewCount;
    private String sellerName;
    private String thumbnailPath;
    private LocalDateTime favoritedAt;
}
