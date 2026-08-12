package nct.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

// 담당자 7 · REQ-AUC-027/F-SVC-012: 거래 문제 접수의 잠금·조건부 보류 SQL 계약을 검증한다.
class TradeDisputeMapperContractTest {

    @Test
    void locksTradeWithServerOwnedTypeMethodParticipantsAndStatus() throws IOException {
        String mapper = loadNormalizedMapper();

        assertThat(mapper)
                .contains("t.SLLR_USR_SN AS sellerUserId")
                .contains("t.BYPR_USR_SN AS buyerUserId")
                .contains("t.REQ_USR_SN AS requesterUserId")
                .contains("t.PRV_USR_SN AS providerUserId")
                .contains("t.TRD_TYPE_CD AS tradeTypeCode")
                .contains("t.TRD_METHOD_CD AS tradeMethodCode")
                .contains("t.TRD_STATUS_CD AS tradeStatusCode")
                .contains("WHERE t.TRD_SN = #{tradeId} FOR UPDATE");
    }

    @Test
    void holdsOnlyActiveTradeStatesAfterDisputeWasSaved() throws IOException {
        String mapper = loadNormalizedMapper();
        String holdStatement = mapper.substring(
                mapper.indexOf("<update id=\"holdTradeForDispute\">"),
                mapper.indexOf("</update>", mapper.indexOf("<update id=\"holdTradeForDispute\">")));

        assertThat(holdStatement)
                .contains("TRD_STATUS_CD = 'TRDC0007'")
                .contains("WHERE TRD_SN = #{tradeId}")
                .contains("TRD_TYPE_CD IN ('TRDC0001', 'TRDC0002')")
                .contains("TRD_STATUS_CD IN ('TRDC0003', 'TRDC0004', 'TRDC0005')")
                .doesNotContain("TRD_TYPE_CD = 'TRDC0002'");
    }

    @Test
    void preservesFirstCompletionRequesterWhenHoldingWaitingConfirmationTrade() throws IOException {
        String mapper = loadNormalizedMapper();
        String holdStatement = mapper.substring(
                mapper.indexOf("<update id=\"holdTradeForDispute\">"),
                mapper.indexOf("</update>", mapper.indexOf("<update id=\"holdTradeForDispute\">")));

        assertThat(holdStatement)
                .contains("WHEN TRD_STATUS_CD = 'TRDC0005' THEN TRD_UPDT_ID")
                .contains("ELSE #{updaterId}")
                .containsSubsequence(
                        "SET TRD_UPDT_ID = CASE",
                        "TRD_STATUS_CD = 'TRDC0007'");
    }

    private String loadNormalizedMapper() throws IOException {
        ClassPathResource resource = new ClassPathResource("mapper/trade/TradeMapper.xml");
        return resource.getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
