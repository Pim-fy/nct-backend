package nct.trade.port;

import java.util.List;

/** 담당자 7 · F-OPS-020: 거래·정산 보류 결과입니다. */
public record MemberTradeRestrictionResult(
        List<RestrictedTrade> restrictedTrades,
        int heldSettlementCount) {

    public int restrictedTradeCount() {
        return restrictedTrades.size();
    }
}
