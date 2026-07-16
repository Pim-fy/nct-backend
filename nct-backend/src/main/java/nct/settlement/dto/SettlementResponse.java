package nct.settlement.dto;

import java.time.format.DateTimeFormatter;

import lombok.Builder;
import lombok.Getter;
import nct.settlement.domain.Settlement;
import nct.settlement.domain.SettlementStatus;

@Getter
@Builder
public class SettlementResponse {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Long id;
    private final Long tradeId;
    private final long amount;
    private final String statusCd;
    private final String statusName;
    private final String regDate;

    public static SettlementResponse from(Settlement settlement) {
        return SettlementResponse.builder()
                .id(settlement.getStlmSn())
                .tradeId(settlement.getTrdSn())
                .amount(settlement.getStlmAmt())
                .statusCd(settlement.getStlmStatusCd())
                .statusName(statusName(settlement.getStlmStatusCd()))
                .regDate(settlement.getStlmRegDt() == null ? null : settlement.getStlmRegDt().format(DATE_FORMATTER))
                .build();
    }

    private static String statusName(String code) {
        if (SettlementStatus.PENDING.getCode().equals(code)) {
            return "대기";
        }
        if (SettlementStatus.ON_HOLD.getCode().equals(code)) {
            return "보류";
        }
        if (SettlementStatus.COMPLETED.getCode().equals(code)) {
            return "완료";
        }
        return code;
    }
}
