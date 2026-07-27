package nct.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 경매 종료 흐름이 신규 생성 또는 기존 거래 재사용 여부를 판단할 수 있게 반환하는 결과다. */
@Getter
@AllArgsConstructor
public class AuctionTradeCreateResult {

    private final long tradeSn;
    private final String tradeStatusCode;
    private final boolean created;

    /** created가 false면 같은 상품의 기존 거래를 멱등 반환한 결과다. */
    public boolean isExistingTrade() {
        return !created;
    }
}
