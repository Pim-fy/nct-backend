package nct.trade.port;

import nct.trade.dto.TradeSettlementReference;

/**
 * 정산 도메인에 거래 유형과 보관금 원본 참조만 제공하는 읽기 전용 공개 계약이다.
 * 소비자는 TradeService나 TradeMapper를 직접 참조하지 않는다.
 */
public interface TradeSettlementReferenceReader {

    TradeSettlementReference getByTradeSn(long tradeSn);
}
