package nct.trade.dto;

import lombok.Data;

/**
 * 거래 문제 접수 처리에서 TRADE 행 잠금과 함께 전달하는 거래 당사자·상태 정보다.
 * 물건 거래의 판매자/구매자와 서비스 거래의 요청자/제공자 중 해당 거래 유형의 값만 채워진다.
 */
@Data
public class TradeDisputeTarget {

    private long tradeSn;
    private Long sellerUserId;
    private Long buyerUserId;
    private Long requesterUserId;
    private Long providerUserId;
    private String tradeTypeCode;
    private String tradeMethodCode;
    private String tradeStatusCode;
}
