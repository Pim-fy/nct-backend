package nct.auction.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuctionStatusSummaryResponse {

    private Long prdSn;
    private Long aucSn;
    private String aucStatusCd;
    private String aucStatusNm;
    // @ai_generated 판매 목록이 경매 단계의 가격 의미를 정확히 표시하도록 제공하는 읽기 전용 요약값입니다.
    private BigDecimal currentPrice;
    private Long bidCount;
}
