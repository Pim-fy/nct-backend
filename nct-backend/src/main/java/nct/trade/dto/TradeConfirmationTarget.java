package nct.trade.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 거래 완료 확인 시 잠금 조회로 확보하는 최소 당사자·상태 정보다.
 * 첫 확인자와 상대방을 구분해 두 당사자 확인이 모두 끝났을 때만 완료 처리한다.
 */
@Data
public class TradeConfirmationTarget {

    private Long tradeId;
    private Long sellerUserId;
    private Long buyerUserId;
    private String tradeStatus;
    private String tradeMethod;
    private String completionRequesterId;
    private BigDecimal tradeAmount;
}
