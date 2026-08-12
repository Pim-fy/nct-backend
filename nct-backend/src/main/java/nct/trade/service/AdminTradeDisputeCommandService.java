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
 * 담당자 7 · F-OPS-005/006: 거래 신고 판정에 따른 TRADEㆍABUSE_REPORT_TRADE 상태를 조건부로 변경합니다.
 * 포인트·정산은 관리자 오케스트레이터가 각 소유 서비스 계약으로 처리합니다.
 */
@Service
@RequiredArgsConstructor
public class AdminTradeDisputeCommandService implements AdminTradeDisputeCommandPort {

    private static final String RECEIVED = "ABSC0001";
    private static final String PROCESSING = "ABSC0002";
    private static final String ON_HOLD = "TRDC0007";
    private static final String CANCELED = "TRDC0008";
    private static final Set<String> RESTORABLE_TRADE_STATUSES =
            Set.of("TRDC0003", "TRDC0004", "TRDC0005");

    private final AdminTradeDisputeCommandMapper mapper;

    @Override
    @Transactional
    public AdminTradeDisputeDecisionTarget lockByReportSn(Long reportSn) {
        if (reportSn == null || reportSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        AdminTradeDisputeDecisionTarget target = mapper.findForUpdate(reportSn);
        if (target == null) {
            throw new CustomException(ErrorCode.NOT_FOUND, "존재하지 않는 거래 신고입니다.");
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
        updateTradeReportResult(target, resultCode, adminUserSn);
    }

    @Override
    @Transactional
    public void restoreAndClose(
            AdminTradeDisputeDecisionTarget target,
            String resultCode,
            String reason,
            Long adminUserSn) {
        validateOpenTarget(target, adminUserSn);
        String previousStatus = target.getPreviousTradeStatusCode();
        if (previousStatus == null || !RESTORABLE_TRADE_STATUSES.contains(previousStatus)) {
            throw new CustomException(
                    ErrorCode.CONFLICT,
                    "신고 접수 전 거래 상태를 확인할 수 없어 자동 복구할 수 없습니다.");
        }
        updateTrade(target, previousStatus, reason, adminUserSn);
        updateTradeReportResult(target, resultCode, adminUserSn);
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
        updateTradeReportResult(target, resultCode, adminUserSn);
    }

    private void validateOpenTarget(AdminTradeDisputeDecisionTarget target, Long adminUserSn) {
        if (target == null
                || target.getReportSn() == null
                || target.getTradeSn() == null
                || adminUserSn == null
                || adminUserSn <= 0
                || (!RECEIVED.equals(target.getReportStatusCode())
                    && !PROCESSING.equals(target.getReportStatusCode()))) {
            throw new CustomException(ErrorCode.CONFLICT, "이미 처리됐거나 판정할 수 없는 거래 신고입니다.");
        }
    }

    private void updateTrade(
            AdminTradeDisputeDecisionTarget target,
            String targetStatus,
            String reason,
            Long adminUserSn) {
        String updaterId = String.valueOf(adminUserSn);
        if (mapper.updateTradeStatus(
                target.getTradeSn(), ON_HOLD, targetStatus,
                target.getRemainingAutoCompleteSeconds(), updaterId) != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "거래 상태가 변경되어 신고 판정을 완료할 수 없습니다.");
        }
        if (mapper.insertTradeStatusHistory(
                target.getTradeSn(), targetStatus, reason, updaterId) != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "거래 상태 이력을 기록하지 못했습니다.");
        }
    }

    private void updateTradeReportResult(
            AdminTradeDisputeDecisionTarget target,
            String resultCode,
            Long adminUserSn) {
        String updaterId = String.valueOf(adminUserSn);
        if (mapper.updateTradeReportResult(
                target.getReportSn(), resultCode, updaterId) != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "거래 신고 상태가 변경되어 판정을 완료할 수 없습니다.");
        }
    }
}
