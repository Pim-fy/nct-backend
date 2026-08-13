package nct.trade.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 담당자 7 · F-OPS-005/006: 관리자 거래 신고 판정 트랜잭션이 잠그는 신고·거래 상태입니다.
 * 개인정보 원문은 포함하지 않고 금융 처리에 필요한 식별자와 상태만 전달합니다.
 */
@Getter
@Setter
public class AdminTradeDisputeDecisionTarget {

    private Long reportSn;
    private Long tradeSn;
    private Long reporterUserSn;
    private String reportStatusCode;
    private String tradeDecisionResultCode;
    private String previousTradeStatusCode;
    private Long remainingAutoCompleteSeconds;
    private boolean settlementHoldApplied;
    private boolean chatClosed;
    private String tradeTypeCode;
    private String tradeStatusCode;
    private Long bidSn;
    private Long sellerUserSn;
    private Long buyerUserSn;
    private Long requesterUserSn;
    private Long providerUserSn;
}
