package nct.trade.dto;

import lombok.Data;

/**
 * 관리자 판매자 취소 승인 시 잠금 조회로 확보하는 거래의 최소 정보다.
 * 완료·보류·기취소 거래에는 상태 변경을 허용하지 않도록 현재 상태를 함께 확인한다.
 */
@Data
public class TradeCancellationTarget {

    private long tradeId;
    private String tradeStatus;
}
