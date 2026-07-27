package nct.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 경매 도메인이 거래 생성 결과를 다시 호출해도 같은 거래를 식별할 수 있게 반환하는 공개 결과다.
 * created가 false면 새 INSERT 없이 기존 상품 거래를 반환한 멱등 처리 결과다.
 */
@Getter
@AllArgsConstructor
public class MaterialTradeCreateResult {

    private final long tradeId;
    private final String tradeStatusCode;
    private final boolean created;
}
