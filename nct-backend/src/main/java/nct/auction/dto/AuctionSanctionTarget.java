package nct.auction.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7 · 신고 제재: 회원과 연관된 경매의 중지·취소 판단에 필요한 잠금 조회 결과입니다. */
@Getter
@Setter
@NoArgsConstructor
public class AuctionSanctionTarget {

    private Long auctionId;
    private Long sellerUserSn;
    private Long highestBidId;
    private Long highestBidderUserSn;
    private String auctionStatusCode;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime updatedAt;
    private LocalDateTime databaseNow;
    private Long tradeSn;
    private String tradeStatusCode;
}
