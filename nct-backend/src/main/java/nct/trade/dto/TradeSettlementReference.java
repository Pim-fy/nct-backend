package nct.trade.dto;

import lombok.Data;

/**
 * 정산 도메인이 거래 유형별 보관금 참조를 복원할 때 사용하는 읽기 전용 결과다.
 * 물건 거래는 BID_SN을 사용하고, 서비스 거래는 BID_SN이 null일 수 있다.
 */
@Data
public class TradeSettlementReference {

    private long tradeSn;
    private String tradeTypeCode;
    private Long bidSn;
}
