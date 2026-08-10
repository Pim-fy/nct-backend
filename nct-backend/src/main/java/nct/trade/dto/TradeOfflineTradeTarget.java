package nct.trade.dto;

import lombok.Data;

/** 일정 협의 API에서 거래 당사자·방식·상태를 잠근 뒤 사용하는 대상이다. */
@Data
public class TradeOfflineTradeTarget {

    private Long tradeId;
    private Long sellerUserId;
    private Long buyerUserId;
    private String tradeStatus;
    private String tradeMethod;
}
