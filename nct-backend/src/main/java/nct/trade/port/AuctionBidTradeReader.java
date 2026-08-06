package nct.trade.port;

import java.util.Collection;
import java.util.List;

import nct.trade.dto.AuctionBidTradeReference;

/**
 * 경매 입찰 이력 도메인에 본인 낙찰 입찰과 물건 거래의 연결만 일괄 제공하는 읽기 전용 계약이다.
 * 소비자는 TRADE Mapper를 직접 참조하지 않는다.
 */
public interface AuctionBidTradeReader {

    List<AuctionBidTradeReference> findByBuyerAndBidSns(
            long buyerUserId,
            Collection<Long> bidSns);
}
