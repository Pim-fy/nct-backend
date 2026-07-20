package nct.auction.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuctionCancelRequestRecord {

    private Long cancelRequestSn;
    private Long auctionId;
    private Long requesterUserId;
    private String reason;
    private String approvalYn;
    private Long processorUserId;
    private String processReason;
    private LocalDateTime processDateTime;
}
