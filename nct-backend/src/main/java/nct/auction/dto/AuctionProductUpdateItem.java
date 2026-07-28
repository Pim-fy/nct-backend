package nct.auction.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuctionProductUpdateItem {

    private Long updateId;
    private String title;
    private String content;
    private LocalDateTime registeredAt;
    private LocalDateTime updatedAt;
}
