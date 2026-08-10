package nct.ops.servicequery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** 담당자 7 · F-OPS-021: 거래·정산·원장 조회가 목록 단위 IN 배치 계약인지 확인합니다. */
class AdminServiceFlowMapperContractTest {

    @Test
    void flowQueriesUseBatchInClausesAndStableAliases() throws IOException {
        String trade = resource("/mapper/trade/TradeMapper.xml");
        String settlement = resource("/mapper/settlement/SettlementMapper.xml");
        String point = resource("/mapper/point/PointMapper.xml");

        assertThat(trade)
                .contains("<select id=\"findAdminServiceTradeSummaries\"")
                .contains("collection=\"serviceRequestIds\"")
                .contains("AS activeDisputeCount")
                .contains("AS unsupportedDisputeCount");
        assertThat(settlement)
                .contains("<select id=\"findAdminSummariesByTradeIds\"")
                .contains("collection=\"tradeIds\"")
                .contains("AS settlementId");
        assertThat(point)
                .contains("<select id=\"findAdminEscrowSummaries\"")
                .contains("collection=\"tradeIds\"")
                .contains("AS escrowDebitedAmount")
                .contains("AS refundedAmount")
                .contains("AS escrowLedgerAmount")
                .contains("AS settledAmount");
    }

    private String resource(String path) throws IOException {
        return new String(
                getClass().getResourceAsStream(path).readAllBytes(),
                StandardCharsets.UTF_8);
    }
}
