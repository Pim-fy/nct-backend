package nct.trade.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 담당자 7 · F-OPS-006: 관리자 분쟁 판정 트랜잭션이 잠그는 분쟁·거래 상태입니다.
 * 개인정보 원문은 포함하지 않고 금융 처리에 필요한 식별자와 상태만 전달합니다.
 */
@Getter
@Setter
public class AdminTradeDisputeDecisionTarget {

    private Long disputeSn;
    private Long tradeSn;
    private Long disputerUserSn;
    private String disputeStatusCode;
    private String disputeResultCode;
    private String previousTradeStatusCode;
    private String tradeTypeCode;
    private String tradeStatusCode;
    private Long bidSn;
    private Long sellerUserSn;
    private Long buyerUserSn;
    private Long requesterUserSn;
    private Long providerUserSn;
}
