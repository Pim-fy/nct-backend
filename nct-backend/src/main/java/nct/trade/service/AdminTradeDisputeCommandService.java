package nct.trade.service;

import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.trade.dto.AdminTradeDisputeDecisionTarget;
import nct.trade.mapper.AdminTradeDisputeCommandMapper;
import nct.trade.port.AdminTradeDisputeCommandPort;

/**
 * 담당자 7 · F-OPS-006: 분쟁 판정에 따른 TRADE·TRADE_DISPUTE 상태를 조건부로 변경합니다.
 * 포인트·정산은 관리자 오케스트레이터가 각 소유 서비스 계약으로 처리합니다.
 */
@Service
@RequiredArgsConstructor
public class AdminTradeDisputeCommandService implements AdminTradeDisputeCommandPort {

    private static final String RECEIVED = "TRDC0016";
    private static final String PROCESSING = "TRDC0017";
    private static final String ON_HOLD = "TRDC0007";
    private static final String CANCELED = "TRDC0008";
    private static final Set<String> RESTORABLE_TRADE_STATUSES =
            Set.of("TRDC0003", "TRDC0004", "TRDC0005");

    private final AdminTradeDisputeCommandMapper mapper;

    @Override
    @Transactional
    public AdminTradeDisputeDecisionTarget lockByDisputeSn(Long disputeSn) {
        if (disputeSn == null || disputeSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        AdminTradeDisputeDecisionTarget target = mapper.findForUpdate(disputeSn);
        if (target == null) {
            throw new CustomException(ErrorCode.NOT_FOUND, "존재하지 않는 거래 분쟁입니다.");
        }
        return target;
    }

    @Override
    @Transactional
    public void keepOnHold(
            AdminTradeDisputeDecisionTarget target,
            String resultCode,
            String reason,
            Long adminUserSn) {
        validateOpenTarget(target, adminUserSn);
        if (!ON_HOLD.equals(target.getTradeStatusCode())) {
            throw new CustomException(ErrorCode.CONFLICT, "보류 상태의 거래만 정산 보류를 유지할 수 있습니다.");
        }
        updateDispute(target, resultCode, PROCESSING, reason, adminUserSn);
    }

    @Override
    @Transactional
    public void restoreAndClose(
            AdminTradeDisputeDecisionTarget target,
            String resultCode,
            String disputeStatusCode,
            String reason,
            Long adminUserSn) {
        validateOpenTarget(target, adminUserSn);
        String previousStatus = target.getPreviousTradeStatusCode();
        if (previousStatus == null || !RESTORABLE_TRADE_STATUSES.contains(previousStatus)) {
            throw new CustomException(
                    ErrorCode.CONFLICT,
                    "분쟁 접수 전 거래 상태를 확인할 수 없어 자동 복구할 수 없습니다.");
        }
        updateTrade(target, previousStatus, reason, adminUserSn);
        updateDispute(target, resultCode, disputeStatusCode, reason, adminUserSn);
    }

    @Override
    @Transactional
    public void cancelAndClose(
            AdminTradeDisputeDecisionTarget target,
            String resultCode,
            String reason,
            Long adminUserSn) {
        validateOpenTarget(target, adminUserSn);
        updateTrade(target, CANCELED, reason, adminUserSn);
        updateDispute(target, resultCode, "TRDC0018", reason, adminUserSn);
    }

    private void validateOpenTarget(AdminTradeDisputeDecisionTarget target, Long adminUserSn) {
        if (target == null
                || target.getDisputeSn() == null
                || target.getTradeSn() == null
                || adminUserSn == null
                || adminUserSn <= 0
                || (!RECEIVED.equals(target.getDisputeStatusCode())
                    && !PROCESSING.equals(target.getDisputeStatusCode()))) {
            throw new CustomException(ErrorCode.CONFLICT, "이미 처리됐거나 판정할 수 없는 거래 분쟁입니다.");
        }
    }

    private void updateTrade(
            AdminTradeDisputeDecisionTarget target,
            String targetStatus,
            String reason,
            Long adminUserSn) {
        String updaterId = String.valueOf(adminUserSn);
        if (mapper.updateTradeStatus(
                target.getTradeSn(), ON_HOLD, targetStatus, updaterId) != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "거래 상태가 변경되어 분쟁 판정을 완료할 수 없습니다.");
        }
        if (mapper.insertTradeStatusHistory(
                target.getTradeSn(), targetStatus, reason, updaterId) != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "거래 상태 이력을 기록하지 못했습니다.");
        }
    }

    private void updateDispute(
            AdminTradeDisputeDecisionTarget target,
            String resultCode,
            String statusCode,
            String reason,
            Long adminUserSn) {
        String updaterId = String.valueOf(adminUserSn);
        if (mapper.updateDisputeDecision(
                target.getDisputeSn(), resultCode, statusCode, reason,
                adminUserSn, updaterId) != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "거래 분쟁 상태가 변경되어 판정을 완료할 수 없습니다.");
        }
    }
}
