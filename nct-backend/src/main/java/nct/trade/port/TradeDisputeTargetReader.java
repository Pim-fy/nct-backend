package nct.trade.port;

import nct.trade.dto.TradeDisputeTarget;

/**
 * 거래 문제 도메인이 TRADE Mapper를 직접 참조하지 않고 잠금 대상 거래를 조회하는 공개 계약이다.
 * 호출 측 트랜잭션에 참여하며, 반환 뒤 분쟁 이력 생성·정산 보류까지 같은 트랜잭션으로 처리한다.
 */
public interface TradeDisputeTargetReader {

    TradeDisputeTarget lockByTradeSn(Long tradeSn);
}
