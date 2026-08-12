package nct.trade.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.abuse.port.ActiveAbuseReportReferenceReader;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.trade.mapper.TradeMapper;
import nct.trade.port.ActiveTradeIncidentReader;

/** 담당자 7 · F-OPS-007: 신고 상위 사건과 기존 분쟁 행을 하나의 거래 안전 판정으로 조립합니다. */
@Service
@RequiredArgsConstructor
public class TradeIncidentStatusQueryService implements ActiveTradeIncidentReader {

    private final ActiveAbuseReportReferenceReader activeReportReferenceReader;
    private final TradeMapper tradeMapper;

    @Override
    @Transactional(readOnly = true)
    public boolean hasOtherOpenIncident(Long tradeSn, Long excludedReportSn) {
        if (tradeSn == null || tradeSn <= 0
                || (excludedReportSn != null && excludedReportSn <= 0)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return activeReportReferenceReader.hasOtherActiveReportLinkedToTrade(
                tradeSn,
                excludedReportSn)
                || tradeMapper.hasOtherOpenTradeDispute(tradeSn, excludedReportSn);
    }
}
