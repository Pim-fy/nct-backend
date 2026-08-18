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
                .contains("AS quoteAmount")
                .contains("AS quoteStatusCode")
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

    /** 담당자 7 · F-OPS-005: 관리자 거래 상세 SQL도 레코드 생성자 계약을 빠짐없이 제공합니다. */
    @Test
    void adminServiceTradeDetailProvidesChatStatusContract() throws IOException {
        String trade = resource("/mapper/trade/TradeMapper.xml");
        int queryStart = trade.indexOf("<select id=\"findAdminServiceTradeDetail\"");
        int queryEnd = trade.indexOf("</select>", queryStart);

        assertThat(queryStart).isGreaterThanOrEqualTo(0);
        assertThat(queryEnd).isGreaterThan(queryStart);
        assertThat(trade.substring(queryStart, queryEnd))
                .contains("END AS chatRoomStatus")
                .contains("FALSE AS chatAvailable")
                .contains("FALSE AS cancellationDecisionAvailable");
    }

    private String resource(String path) throws IOException {
        return new String(
                getClass().getResourceAsStream(path).readAllBytes(),
                StandardCharsets.UTF_8);
    }
}
