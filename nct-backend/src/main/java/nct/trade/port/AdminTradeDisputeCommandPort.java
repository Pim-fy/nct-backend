package nct.trade.port;

import nct.trade.dto.AdminTradeDisputeDecisionTarget;

/** 담당자 7 · F-OPS-006: TRADE·TRADE_DISPUTE 소유 경계가 제공하는 관리자 판정 명령입니다. */
public interface AdminTradeDisputeCommandPort {

    AdminTradeDisputeDecisionTarget lockByDisputeSn(Long disputeSn);

    void keepOnHold(
            AdminTradeDisputeDecisionTarget target,
            String resultCode,
            String reason,
            Long adminUserSn);

    void restoreAndClose(
            AdminTradeDisputeDecisionTarget target,
            String resultCode,
            String disputeStatusCode,
            String reason,
            Long adminUserSn);

    void cancelAndClose(
            AdminTradeDisputeDecisionTarget target,
            String resultCode,
            String reason,
            Long adminUserSn);
}
