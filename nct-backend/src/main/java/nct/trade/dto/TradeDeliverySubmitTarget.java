package nct.trade.dto;

import lombok.Data;

/** 발송 제출 시 판매자·배송 거래·현재 상태를 잠금 조회한 결과다. */
@Data
public class TradeDeliverySubmitTarget {

    private Long tradeId;
    private Long deliveryId;
    private String tradeStatus;
}
