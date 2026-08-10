package nct.ops.operation.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import nct.member.dto.AdminMemberIdentityResponse;

/** 담당자 7 · F-OPS-005: 관리자 거래 분쟁 목록 한 건입니다. */
@Getter
@Builder
public class AdminDisputeListItemResponse {

    private final Long disputeSn;
    private final Long tradeSn;
    private final Long disputerUserSn;
    private final AdminMemberIdentityResponse disputerMember;
    private final String disputeTypeCode;
    private final String disputeTypeName;
    private final String disputeStatusCode;
    private final String disputeStatusName;
    private final String tradeTypeCode;
    private final String tradeTypeName;
    private final String tradeStatusCode;
    private final String tradeStatusName;
    private final Long settlementSn;
    private final String settlementStatusCode;
    private final String settlementStatusName;
    private final boolean settlementOnHold;
    private final LocalDateTime registeredAt;
}
