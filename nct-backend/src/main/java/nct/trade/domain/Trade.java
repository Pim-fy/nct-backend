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
    /** 서비스 거래의 의뢰자다. 물건 거래에서는 null이다. */
    private Long requesterUserId;
    /** 서비스 거래의 제공자다. 물건 거래에서는 null이다. */
    private Long providerUserId;
    /** 서비스 거래의 원본 서비스 요청 번호다. 물건 거래에서는 null이다. */
    private Long serviceRequestId;
    /** 서비스 거래 생성의 근거가 되는 선택 견적 번호다. 물건 거래에서는 null이다. */
    private Long quoteId;
    private String tradeTypeCode;
    private String tradeStatusCode;
    /** 상품 등록 방식이 아닌 낙찰 뒤 확정된 실제 거래방식이다. */
    private String tradeMethodCode;
    private BigDecimal tradeAmount;
    private LocalDateTime autoCompleteAt;
    private LocalDateTime createdAt;
}
