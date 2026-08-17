package nct.trade.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.abuse.port.ReportTargetHoldPort;
import nct.abuse.port.ReportTargetHoldResult;
import nct.abuse.port.ReportTargetRestoreCommand;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.settlement.service.SettlementService;
import nct.trade.dto.TradeDisputeTarget;
import nct.trade.mapper.TradeMapper;

/** 담당자 7 · F-OPS-007/F-OPS-008: 신고에 참조된 거래 한 건과 대기 정산만 보류합니다. */
@Service
@RequiredArgsConstructor
public class TradeReportTargetHoldService implements ReportTargetHoldPort {

    private static final String REFERENCE_TYPE = "REFC0005";
    private static final String HOLD_STATUS = "TRDC0007";
    private static final Set<String> PAUSABLE = Set.of("TRDC0003", "TRDC0004", "TRDC0005");

    private final TradeMapper tradeMapper;
    private final ObjectProvider<SettlementService> settlementServiceProvider;

    @Override
    public String referenceTypeCode() {
        return REFERENCE_TYPE;
    }

    @Override
    @Transactional
    public ReportTargetHoldResult pause(Long referenceSn, String actorId) {
        validate(referenceSn, actorId);
        TradeDisputeTarget target = tradeMapper.findTradeReportTargetForUpdate(referenceSn);
        if (target == null) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }

        String previous = target.getTradeStatusCode();
        if (HOLD_STATUS.equals(previous)) {
            return result(target, false, true, false, "다른 신고로 이미 거래가 보류 중입니다.");
        }
        if (!PAUSABLE.contains(previous)) {
            return result(target, false, false, false, "현재 거래 상태는 신고 보류 대상이 아닙니다.");
        }

        if (tradeMapper.holdTradeForReport(referenceSn, actorId) != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "거래 상태가 변경되어 신고 보류를 적용할 수 없습니다.");
        }
        String reason = "신고 접수에 따른 거래 단건 보류";
        tradeMapper.insertStatusHistory(referenceSn, HOLD_STATUS, reason);
        boolean settlementHeld = settlementServiceProvider.getObject()
                .holdUpByTradeIfPending(referenceSn, reason);
        return result(target, true, false, settlementHeld, "신고 접수와 함께 해당 거래를 보류했습니다.");
    }

    @Override
    @Transactional
    public boolean restore(ReportTargetRestoreCommand command) {
        validateRestore(command);
        int changed = tradeMapper.restoreTradeAfterMemberRestriction(
                command.referenceSn(),
                command.previousStatusCode(),
                nonNegative(command.remainingSeconds()),
                command.actorId());
        if (changed == 0) {
            return false;
        }
        tradeMapper.insertStatusHistory(
                command.referenceSn(),
                command.previousStatusCode(),
                "마지막 활성 신고 해소에 따른 거래 복구");
        if (command.settlementHoldApplied()) {
            settlementServiceProvider.getObject().resumeByTradeIfOnHold(
                    command.referenceSn(),
                    parseActorId(command.actorId()));
        }
        return true;
    }

    private ReportTargetHoldResult result(
            TradeDisputeTarget target,
            boolean changed,
            boolean alreadyOnReportHold,
            boolean settlementHoldApplied,
            String message) {
        LocalDateTime now = target.getDatabaseNow() == null
                ? LocalDateTime.now()
                : target.getDatabaseNow();
        Long remainingSeconds = target.getAutoCompleteAt() == null
                ? null
                : Math.max(0L, Duration.between(now, target.getAutoCompleteAt()).getSeconds());
        return new ReportTargetHoldResult(
                target.getTradeSn(),
                changed,
                alreadyOnReportHold,
                target.getTradeStatusCode(),
                null,
                target.getAutoCompleteAt(),
                null,
                remainingSeconds,
                settlementHoldApplied,
                message);
    }

    private void validate(Long referenceSn, String actorId) {
        if (referenceSn == null || referenceSn <= 0 || actorId == null || actorId.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateRestore(ReportTargetRestoreCommand command) {
        if (command == null
                || command.referenceSn() == null || command.referenceSn() <= 0
                || !PAUSABLE.contains(command.previousStatusCode())
                || command.actorId() == null || command.actorId().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private Long nonNegative(Long value) {
        return value == null ? null : Math.max(0L, value);
    }

    private long parseActorId(String actorId) {
        try {
            long parsed = Long.parseLong(actorId);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "관리자 번호가 올바르지 않습니다.");
        }
    }
}
