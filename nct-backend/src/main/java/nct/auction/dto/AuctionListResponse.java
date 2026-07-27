package nct.auction.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuctionListResponse {

    private List<AuctionListItem> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
