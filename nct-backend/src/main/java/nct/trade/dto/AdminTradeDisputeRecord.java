package nct.trade.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 담당자 7 · F-OPS-005: TRADE_DISPUTE와 TRADE의 관리자용 읽기 결과입니다.
 * 이름·연락처·주소 등 개인정보는 포함하지 않습니다.
 */
@Getter
@Setter
public class AdminTradeDisputeRecord {

    private Long disputeSn;
    private Long tradeSn;
    private Long disputerUserSn;
    private String disputeTypeCode;
    private String disputeStatusCode;
    private LocalDateTime registeredAt;
    private LocalDateTime updatedAt;
    private String tradeTypeCode;
    private String tradeStatusCode;
    private Long sellerUserSn;
    private Long buyerUserSn;
    private Long requesterUserSn;
    private Long providerUserSn;
    private Long productSn;
    private Long serviceRequestSn;
}
