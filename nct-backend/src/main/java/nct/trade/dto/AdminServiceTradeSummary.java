package nct.trade.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7 · F-OPS-021: 관리자 통합상태 조립에 제공하는 서비스 거래 요약입니다. */
@Getter
@Setter
@NoArgsConstructor
public class AdminServiceTradeSummary {
    private Long serviceRequestId;
    private Long tradeId;
    private Long quoteId;
    private String tradeStatusCode;
    private String tradeStatusName;
    private int activeDisputeCount;
    private int unsupportedDisputeCount;
    private Long activeDisputeId;
    private String activeDisputeStatusCode;
    private String activeDisputeStatusName;
}
