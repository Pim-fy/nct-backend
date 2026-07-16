package nct.settlement.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.ErrorCode;
import nct.settlement.domain.Settlement;
import nct.settlement.domain.SettlementStatus;
import nct.settlement.exception.SettlementException;
import nct.settlement.mapper.SettlementMapper;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementMapper settlementMapper;

    @Transactional
    public long createPending(long trdSn, long usrSn, long amount) {
        if (amount <= 0) {
            throw new SettlementException(ErrorCode.SETTLEMENT_INVALID_AMOUNT, "정산 금액은 0보다 커야 합니다.");
        }

        Settlement settlement = new Settlement();
        settlement.setTrdSn(trdSn);
        settlement.setUsrSn(usrSn);
        settlement.setStlmAmt(amount);
        settlement.setStlmStatusCd(SettlementStatus.PENDING.getCode());
        settlementMapper.insert(settlement);
        return settlement.getStlmSn();
    }

    @Transactional
    public void complete(long stlmSn) {
        requireStatus(stlmSn, SettlementStatus.PENDING, "완료");
        settlementMapper.updateStatus(stlmSn, SettlementStatus.COMPLETED.getCode());
    }

    @Transactional
    public void holdUp(long stlmSn, String reason) {
        requireStatus(stlmSn, SettlementStatus.PENDING, "보류");
        settlementMapper.updateStatus(stlmSn, SettlementStatus.ON_HOLD.getCode());
    }

    @Transactional
    public void resume(long stlmSn) {
        requireStatus(stlmSn, SettlementStatus.ON_HOLD, "보류 해제");
        settlementMapper.updateStatus(stlmSn, SettlementStatus.PENDING.getCode());
    }

    @Transactional(readOnly = true)
    public List<Settlement> getListByUser(long usrSn) {
        return settlementMapper.selectListByUser(usrSn);
    }

    private Settlement requireStatus(long stlmSn, SettlementStatus expected, String action) {
        Settlement settlement = settlementMapper.selectForUpdate(stlmSn);
        if (settlement == null) {
            throw new SettlementException(ErrorCode.SETTLEMENT_NOT_FOUND, "존재하지 않는 정산 건입니다.");
        }
        if (!expected.getCode().equals(settlement.getStlmStatusCd())) {
            throw new SettlementException(ErrorCode.SETTLEMENT_INVALID_STATUS, action + " 처리할 수 없는 상태입니다.");
        }
        return settlement;
    }
}
