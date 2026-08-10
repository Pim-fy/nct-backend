package nct.trade.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.trade.mapper.TradeMapper;
import nct.trade.port.AdminTradeSummaryReader;

/** 담당자 7 · F-OPS-010: 거래 소유 영역에서 전체 거래 수를 집계합니다. */
@Service
@RequiredArgsConstructor
public class AdminTradeSummaryReaderService implements AdminTradeSummaryReader {

    private final TradeMapper tradeMapper;

    @Override
    @Transactional(readOnly = true)
    public long countAllTrades() {
        return tradeMapper.countAllTrades();
    }
}
