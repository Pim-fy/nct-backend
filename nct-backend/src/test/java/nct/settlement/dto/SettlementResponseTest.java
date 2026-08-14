package nct.settlement.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import nct.settlement.domain.Settlement;
import nct.settlement.domain.SettlementStatus;

class SettlementResponseTest {

    @Test
    void mapsRefundedSettlementStatusName() {
        Settlement settlement = new Settlement();
        settlement.setStlmStatusCd(SettlementStatus.REFUNDED.getCode());
        settlement.setTradeTypeCode("TRDC0002");

        SettlementResponse response = SettlementResponse.from(settlement);

        assertThat(response.getStatusCd()).isEqualTo("STLC0004");
        assertThat(response.getStatusName()).isEqualTo("환불종결");
        assertThat(response.getTradeTypeCode()).isEqualTo("TRDC0002");
    }
}
