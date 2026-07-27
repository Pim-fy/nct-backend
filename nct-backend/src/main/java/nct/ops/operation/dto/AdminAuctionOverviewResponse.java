package nct.ops.operation.dto;

import lombok.Builder;
import lombok.Getter;
import nct.auction.dto.AuctionDetailResponse;
import nct.product.dto.ProductResponse;

/** 담당자 7 · F-OPS-003: 관리자 경매 운영 조회에 필요한 상품·경매·입찰·거래 상태 응답입니다. */
@Getter
@Builder
public class AdminAuctionOverviewResponse {

    private final ProductResponse product;
    private final AuctionDetailResponse auction;
    private final Long tradeSn;
    private final String tradeStatusCode;
}
