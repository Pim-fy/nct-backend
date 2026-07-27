package nct.auction.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuctionRealtimeEvent {

    private final Long auctionId;
    private final String eventType;
}
