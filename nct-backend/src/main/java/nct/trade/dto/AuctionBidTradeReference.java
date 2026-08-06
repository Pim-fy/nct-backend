package nct.trade.dto;

import lombok.Data;

/**
 * 낙찰 입찰 이력에서 본인 물건 거래 상세로 이동할 때 사용하는 읽기 전용 연결 정보다.
 * 거래가 아직 만들어지지 않은 입찰은 조회 결과에 포함하지 않는다.
 */
@Data
public class AuctionBidTradeReference {

    private long bidSn;
    private long tradeId;
}
