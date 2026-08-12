package nct.trade.port;

import java.util.List;

/**
 * 담당자 7 · F-OPS-020: 운영 오케스트레이션이 거래 테이블을 직접 쓰지 않고 호출하는 계약입니다.
 */
public interface MemberTradeRestrictionPort {

    MemberTradeRestrictionResult restrictActiveTrades(MemberTradeRestrictionCommand command);

    List<TradeEnforcementImpact> enforcePermanent(MemberTradeRestrictionCommand command);

    boolean restoreTrade(MemberTradeRestoreCommand command);
}
