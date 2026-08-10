package nct.settlement.dto;

import lombok.Builder;
import lombok.Getter;

/** F-OPS-009 관리자 정산 상태 변경 결과입니다. */
@Getter
@Builder
public class AdminSettlementActionResponse {

    private final Long settlementId;
    private final String previousStatusCode;
    private final String currentStatusCode;
    private final boolean changed;
}
