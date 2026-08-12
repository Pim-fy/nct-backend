package nct.trade.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.settlement.service.SettlementService;
import nct.trade.dto.MemberActiveTradeTarget;
import nct.trade.mapper.TradeMapper;
import nct.trade.port.MemberTradeRestrictionCommand;
import nct.trade.port.MemberTradeRestrictionPort;
import nct.trade.port.MemberTradeRestrictionResult;
import nct.trade.port.MemberTradeRestoreCommand;
import nct.trade.port.RestrictedTrade;
import nct.trade.port.AdminServiceTradeCancellationCommand;
import nct.trade.port.AdminServiceTradeCancellationPort;
import nct.trade.port.TradeEnforcementImpact;

/**
 * 담당자 7 · F-OPS-020: 계정 제한 대상자의 진행 거래와 대기 정산을 한 트랜잭션에서 보류합니다.
 * 완료·취소·기존 보류 거래는 조회 조건에서 제외하며, 실제 전이된 거래만 이력을 남깁니다.
 */
@Service
@RequiredArgsConstructor
public class MemberTradeRestrictionService implements MemberTradeRestrictionPort {

    private static final String HOLD_STATUS = "TRDC0007";
    private static final String SERVICE_TRADE = "TRDC0002";
    private static final Set<String> RESTORABLE_STATUSES =
            Set.of("TRDC0003", "TRDC0004", "TRDC0005");

    private final TradeMapper tradeMapper;
    private final SettlementService settlementService;
    @Autowired
    private AdminServiceTradeCancellationPort serviceTradeCancellationPort;

    @Override
    @Transactional
    public MemberTradeRestrictionResult restrictActiveTrades(MemberTradeRestrictionCommand command) {
        validate(command);

        String updaterId = String.valueOf(command.adminUserSn());
        String historyReason = "회원 계정 제한에 따른 거래 보류: " + command.reason().trim();
        List<RestrictedTrade> restrictedTrades = new ArrayList<>();
        int heldSettlementCount = 0;

        List<MemberActiveTradeTarget> targets = tradeMapper.findActiveTradesByMemberForUpdate(command.userSn());
        for (MemberActiveTradeTarget target : targets) {
            int changed = tradeMapper.holdTradeForMemberRestriction(
                    target.getTradeId(), target.getTradeStatusCode(), updaterId);
            if (changed == 0) {
                continue;
            }

            tradeMapper.insertStatusHistory(target.getTradeId(), HOLD_STATUS, historyReason);
            boolean settlementHeld = settlementService.holdUpByTradeIfPending(
                    target.getTradeId(), historyReason);
            if (settlementHeld) {
                heldSettlementCount++;
            }
            Long remainingSeconds = target.getAutoCompleteAt() == null
                    ? null
                    : Math.max(0L, Duration.between(
                            target.getDatabaseNow() == null
                                    ? java.time.LocalDateTime.now()
                                    : target.getDatabaseNow(),
                            target.getAutoCompleteAt()).getSeconds());
            restrictedTrades.add(new RestrictedTrade(
                    target.getTradeId(),
                    counterpart(target, command.userSn()),
                    target.getTradeStatusCode(),
                    target.getAutoCompleteAt(),
                    remainingSeconds,
                    settlementHeld));
        }

        return new MemberTradeRestrictionResult(List.copyOf(restrictedTrades), heldSettlementCount);
    }

    @Override
    @Transactional
    public List<TradeEnforcementImpact> enforcePermanent(MemberTradeRestrictionCommand command) {
        validate(command);
        String updaterId = String.valueOf(command.adminUserSn());
        String holdReason = "영구 이용정지에 따른 거래 검토 보류: " + command.reason().trim();
        List<TradeEnforcementImpact> impacts = new ArrayList<>();

        for (MemberActiveTradeTarget target :
                tradeMapper.findActiveTradesByMemberForUpdate(command.userSn())) {
            if (SERVICE_TRADE.equals(target.getTradeTypeCode())
                    && serviceTradeCancellationPort.cancel(new AdminServiceTradeCancellationCommand(
                            target.getTradeId(),
                            command.adminUserSn(),
                            command.reason(),
                            command.sourceReportSn()))) {
                impacts.add(new TradeEnforcementImpact(
                        target.getTradeId(),
                        counterpart(target, command.userSn()),
                        "CANCELED",
                        target.getTradeStatusCode(),
                        target.getAutoCompleteAt(),
                        remainingSeconds(target),
                        false,
                        "서비스 거래를 취소하고 의뢰인의 보관금을 반환했습니다."));
                continue;
            }

            if (HOLD_STATUS.equals(target.getTradeStatusCode())) {
                impacts.add(new TradeEnforcementImpact(
                        target.getTradeId(),
                        counterpart(target, command.userSn()),
                        "HELD_FOR_REVIEW",
                        target.getTradeStatusCode(),
                        target.getAutoCompleteAt(),
                        remainingSeconds(target),
                        false,
                        "기존 분쟁 또는 운영 보류 사유를 확인해야 해 자동 취소하지 않았습니다."));
                continue;
            }

            int changed = tradeMapper.holdTradeForMemberRestriction(
                    target.getTradeId(), target.getTradeStatusCode(), updaterId);
            if (changed != 1) {
                throw new CustomException(
                        ErrorCode.CONFLICT,
                        "거래 상태가 변경되어 영구정지 조치를 적용할 수 없습니다.");
            }
            tradeMapper.insertStatusHistory(target.getTradeId(), HOLD_STATUS, holdReason);
            boolean settlementHeld = settlementService.holdUpByTradeIfPending(
                    target.getTradeId(), holdReason);
            impacts.add(new TradeEnforcementImpact(
                    target.getTradeId(),
                    counterpart(target, command.userSn()),
                    "HELD_FOR_REVIEW",
                    target.getTradeStatusCode(),
                    target.getAutoCompleteAt(),
                    remainingSeconds(target),
                    settlementHeld,
                    "자동 환불 계약이 확인되지 않은 거래라 관리자 검토 상태로 보류했습니다."));
        }
        return List.copyOf(impacts);
    }

    @Override
    @Transactional
    public boolean restoreTrade(MemberTradeRestoreCommand command) {
        validateRestore(command);
        int changed = tradeMapper.restoreTradeAfterMemberRestriction(
                command.tradeSn(),
                command.previousStatusCode(),
                command.remainingSeconds(),
                String.valueOf(command.adminUserSn()));
        if (changed == 0) {
            return false;
        }
        tradeMapper.insertStatusHistory(
                command.tradeSn(),
                command.previousStatusCode(),
                "회원 이용정지 해제에 따른 거래 복구: " + command.reason().trim());
        if (command.settlementHeld()) {
            settlementService.resumeByTradeIfOnHold(command.tradeSn(), command.adminUserSn());
        }
        return true;
    }

    private Long counterpart(MemberActiveTradeTarget target, Long restrictedUserSn) {
        return restrictedUserSn.equals(target.getSellerUserId())
                ? target.getBuyerUserId()
                : target.getSellerUserId();
    }

    private void validate(MemberTradeRestrictionCommand command) {
        if (command == null
                || command.userSn() == null
                || command.userSn() <= 0
                || command.adminUserSn() == null
                || command.adminUserSn() <= 0
                || command.reason() == null
                || command.reason().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private Long remainingSeconds(MemberActiveTradeTarget target) {
        return target.getAutoCompleteAt() == null
                ? null
                : Math.max(0L, Duration.between(
                        target.getDatabaseNow() == null
                                ? java.time.LocalDateTime.now()
                                : target.getDatabaseNow(),
                        target.getAutoCompleteAt()).getSeconds());
    }

    private void validateRestore(MemberTradeRestoreCommand command) {
        if (command == null
                || command.tradeSn() == null
                || command.tradeSn() <= 0
                || command.adminUserSn() == null
                || command.adminUserSn() <= 0
                || !RESTORABLE_STATUSES.contains(command.previousStatusCode())
                || command.reason() == null
                || command.reason().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
