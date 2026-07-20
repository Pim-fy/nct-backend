package nct.favorite.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FavoriteAuctionTarget {

    private Long auctionId;
    private Long productId;
    private Long sellerId;
    private String productUseYn;
    private String productStatusCode;
}
