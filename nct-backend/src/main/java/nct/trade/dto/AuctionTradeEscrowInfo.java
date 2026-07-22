package nct.trade.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 경매 도메인이 물건 거래의 보관금 원본을 확인할 때 사용하는 공개 조회 결과다.
 * BID_SN 생성 전 기존 거래는 bidSn이 null일 수 있으므로 호출자가 자동 환불 대상에서 제외한다.
 */
@Data
public class AuctionTradeEscrowInfo {

    private long tradeSn;
    private Long bidSn;
    private long buyerUsrSn;
    private String tradeStatusCd;
    private BigDecimal tradeAmount;
}
