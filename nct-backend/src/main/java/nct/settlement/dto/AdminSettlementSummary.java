package nct.settlement.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7 · F-OPS-021: 관리자 통합상태에 제공하는 거래별 정산 원본 상태입니다. */
@Getter
@Setter
@NoArgsConstructor
public class AdminSettlementSummary {
    private Long tradeId;
    private Long settlementId;
    private String statusCode;
    private String statusName;
}
