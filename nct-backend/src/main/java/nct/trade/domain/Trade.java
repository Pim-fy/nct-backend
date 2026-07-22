package nct.trade.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * TRADE 한 행을 표현하는 물건·서비스 거래 공통 모델이다.
 * 현재 1단계에서는 물건 거래 생성에만 사용하고, 서비스 거래는 2단계에서 확장한다.
 */
@Data
public class Trade {

    private Long trdSn;
    private Long sellerUserId;
    private Long buyerUserId;
    private Long productId;
    /** 경매 낙찰·즉시구매로 생성된 물건 거래의 원본 입찰 번호다. */
    private Long bidId;
    private String tradeTypeCode;
    private String tradeStatusCode;
    private BigDecimal tradeAmount;
    private LocalDateTime autoCompleteAt;
    private LocalDateTime createdAt;
}
