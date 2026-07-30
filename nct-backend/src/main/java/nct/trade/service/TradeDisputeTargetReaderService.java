package nct.trade.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.trade.dto.TradeDisputeTarget;
import nct.trade.mapper.TradeMapper;
import nct.trade.port.TradeDisputeTargetReader;

/** TRADE 기술 소유 경계 안에서 거래 문제 접수 대상 행을 잠근다. */
@Service
@RequiredArgsConstructor
public class TradeDisputeTargetReaderService implements TradeDisputeTargetReader {

    private final TradeMapper tradeMapper;

    @Override
    @Transactional
    public TradeDisputeTarget lockByTradeSn(Long tradeSn) {
        if (tradeSn == null || tradeSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "거래 번호가 올바르지 않습니다.");
        }

        TradeDisputeTarget target = tradeMapper.findTradeDisputeTargetForUpdate(tradeSn);
        if (target == null) {
            throw new CustomException(ErrorCode.NOT_FOUND,
                    "거래 문제 접수 대상 거래를 찾을 수 없습니다: " + tradeSn);
        }

        return target;
    }
}
