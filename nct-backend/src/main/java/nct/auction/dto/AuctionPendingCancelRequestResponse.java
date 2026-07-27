package nct.auction.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuctionPendingCancelRequestResponse {

    private Long cancelRequestSn;
    private Long aucSn;
    private Long requesterUsrSn;
    private String reason;
    private String previousAuctionStatusCode;
    private String currentAuctionStatusCode;
    private LocalDateTime requestedAt;
}
