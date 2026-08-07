package nct.ops.operation.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/** 담당자 7 · F-OPS-005: 개인정보 원문과 파일 데이터를 제외한 분쟁 상세 응답입니다. */
@Getter
@Builder
public class AdminDisputeDetailResponse {

    private final Long disputeSn;
    private final Long tradeSn;
    private final Long disputerUserSn;
    private final String disputeTypeCode;
    private final String disputeTypeName;
    private final String disputeStatusCode;
    private final String disputeStatusName;
    private final String disputeContent;
    private final String disputeResultCode;
    private final String disputeResultName;
    private final String processReason;
    private final Long processorUserSn;
    private final LocalDateTime processedAt;
    private final String previousTradeStatusCode;
    private final String previousTradeStatusName;
    private final String tradeTypeCode;
    private final String tradeTypeName;
    private final String tradeStatusCode;
    private final String tradeStatusName;
    private final Long sellerUserSn;
    private final Long buyerUserSn;
    private final Long requesterUserSn;
    private final Long providerUserSn;
    private final Long productSn;
    private final Long serviceRequestSn;
    private final Long settlementSn;
    private final String settlementStatusCode;
    private final String settlementStatusName;
    private final boolean settlementOnHold;
    private final LocalDateTime registeredAt;
    private final LocalDateTime updatedAt;
}
