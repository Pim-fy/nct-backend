package nct.auction.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7 · F-OPS-007: 신고 대상 경매의 소유자와 표시명만 제공하는 읽기 모델입니다. */
@Getter
@Setter
@NoArgsConstructor
public class AuctionReportReference {

    private Long auctionId;
    private Long sellerUserSn;
    private String title;
    private String statusCode;
}
