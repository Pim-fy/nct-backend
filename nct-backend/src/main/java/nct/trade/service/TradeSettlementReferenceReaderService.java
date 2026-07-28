package nct.trade.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.trade.dto.TradeSettlementReference;
import nct.trade.mapper.TradeMapper;
import nct.trade.port.TradeSettlementReferenceReader;

/** 거래 기술 소유 경계 안에서 정산용 참조값을 조회한다. */
@Service
@RequiredArgsConstructor
public class TradeSettlementReferenceReaderService implements TradeSettlementReferenceReader {

    private final TradeMapper tradeMapper;

    @Override
    @Transactional(readOnly = true)
    public TradeSettlementReference getByTradeSn(long tradeSn) {
        if (tradeSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "거래 번호가 올바르지 않습니다.");
        }

        TradeSettlementReference reference = tradeMapper.findSettlementReferenceByTradeId(tradeSn);
        if (reference == null) {
            throw new CustomException(ErrorCode.NOT_FOUND,
                    "정산 참조를 찾을 수 없는 거래입니다: " + tradeSn);
        }
        return reference;
    }
}
