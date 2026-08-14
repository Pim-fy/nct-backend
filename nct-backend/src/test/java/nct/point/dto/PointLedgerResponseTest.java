package nct.point.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import nct.point.domain.PointLedger;

/** 담당자 7 · 거래 원장이 물건/서비스 상세 경로 구분값을 응답에 보존하는지 검증합니다. */
class PointLedgerResponseTest {

    @Test
    void preservesTradeTypeCodeForTradeReference() {
        PointLedger ledger = new PointLedger();
        ledger.setPtLdgRefTypeCd("REFC0005");
        ledger.setPtLdgRefSn(100L);
        ledger.setTradeTypeCode("TRDC0002");

        PointLedgerResponse response = PointLedgerResponse.from(ledger);

        assertThat(response.getRefTypeCd()).isEqualTo("REFC0005");
        assertThat(response.getRefSn()).isEqualTo(100L);
        assertThat(response.getTradeTypeCode()).isEqualTo("TRDC0002");
    }
}
