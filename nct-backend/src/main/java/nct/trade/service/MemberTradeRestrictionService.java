package nct.trade.service;

import java.util.ArrayList;
import java.util.List;

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
import nct.trade.port.RestrictedTrade;

/**
 * 담당자 7 · F-OPS-020: 계정 제한 대상자의 진행 거래와 대기 정산을 한 트랜잭션에서 보류합니다.
 * 완료·취소·기존 보류 거래는 조회 조건에서 제외하며, 실제 전이된 거래만 이력을 남깁니다.
 */
@Service
@RequiredArgsConstructor
public class MemberTradeRestrictionService implements MemberTradeRestrictionPort {

    private static final String HOLD_STATUS = "TRDC0007";

    private final TradeMapper tradeMapper;
    private final SettlementService settlementService;

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
            if (settlementService.holdUpByTradeIfPending(target.getTradeId(), historyReason)) {
                heldSettlementCount++;
            }
            restrictedTrades.add(new RestrictedTrade(
                    target.getTradeId(), counterpart(target, command.userSn())));
        }

        return new MemberTradeRestrictionResult(List.copyOf(restrictedTrades), heldSettlementCount);
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
}
