package nct.trade.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 경매 도메인이 낙찰 또는 즉시구매를 확정한 뒤 호출하는 내부 거래 생성 계약이다.
 * HTTP 요청 DTO가 아니므로 사용자 입력값을 그대로 받는 용도로 사용하지 않는다.
 */
@Getter
@AllArgsConstructor
public class MaterialTradeCreateCommand {

    private final long sellerUserId;
    private final long buyerUserId;
    private final long productId;
    private final BigDecimal tradeAmount;
}
