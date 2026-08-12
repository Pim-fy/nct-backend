package nct.trade.port;

import nct.trade.dto.AdminTradeDisputeDecisionTarget;

/** 담당자 7 · F-OPS-005/006: 거래와 거래 신고 문맥의 관리자 판정 명령입니다. */
public interface AdminTradeDisputeCommandPort {

    AdminTradeDisputeDecisionTarget lockByReportSn(Long reportSn);

    void keepOnHold(
            AdminTradeDisputeDecisionTarget target,
            String resultCode,
            String reason,
            Long adminUserSn);

    void restoreAndClose(
            AdminTradeDisputeDecisionTarget target,
            String resultCode,
            String reason,
            Long adminUserSn);

    void cancelAndClose(
            AdminTradeDisputeDecisionTarget target,
            String resultCode,
            String reason,
            Long adminUserSn);
}
