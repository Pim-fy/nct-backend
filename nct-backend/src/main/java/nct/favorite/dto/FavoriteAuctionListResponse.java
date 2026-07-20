package nct.favorite.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FavoriteAuctionListResponse {

    private List<FavoriteAuctionItem> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
