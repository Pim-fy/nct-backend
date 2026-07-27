package nct.trade.dto;

import lombok.Data;

/**
 * 거래 완료 확인 요청 시 잠금 조회로 확보하는 최소 당사자·상태 정보다.
 * 구매자만 요청할 수 있고, 알림은 판매자에게 보내기 때문에 두 회원 번호를 함께 보관한다.
 */
@Data
public class TradeConfirmationTarget {

    private Long tradeId;
    private Long sellerUserId;
    private Long buyerUserId;
    private String tradeStatus;
}
