package nct.trade.dto;

import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/** 담당자 7 · F-OPS-020: 계정 제한 시 잠그고 보류할 진행 거래의 최소 정보입니다. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MemberActiveTradeTarget {

    private Long tradeId;
    private Long sellerUserId;
    private Long buyerUserId;
    private String tradeStatusCode;
}
