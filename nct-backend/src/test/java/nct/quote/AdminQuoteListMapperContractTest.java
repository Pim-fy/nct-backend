package nct.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** 담당자 3 견적 조회 계약 / 담당자 7 F-OPS-021 소비: 관리자 목록이 단일 QUOTE 조회인지 검증합니다. */
class AdminQuoteListMapperContractTest {

    @Test
    void adminQuoteListUsesOneQuoteQueryWithoutContentOrAttachments() throws IOException {
        String mapper = new String(
                getClass().getResourceAsStream("/mapper/quote/QuoteMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        int start = mapper.indexOf("<select id=\"findAdminQuoteListItems\"");
        int end = mapper.indexOf("</select>", start);

        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        assertThat(mapper.substring(start, end))
                .contains("q.SVC_REQ_SN AS serviceRequestId")
                .contains("q.QUT_SN AS quoteId")
                .contains("q.USR_SN AS providerUserId")
                .contains("q.QUT_AMT AS amount")
                .contains("q.QUT_STATUS_CD AS statusCode")
                .contains("q.QUT_REVISE_CNT AS reviseCount")
                .contains("q.QUT_REG_DT AS submittedAt")
                .contains("q.QUT_UPDT_DT AS updatedAt")
                .contains("FROM QUOTE q")
                .contains("WHERE q.SVC_REQ_SN = #{serviceRequestId}")
                .contains("ORDER BY q.QUT_REG_DT DESC, q.QUT_SN DESC")
                .doesNotContain(" JOIN ")
                .doesNotContain("QUT_CN")
                .doesNotContain("QUOTE_PHOTO")
                .doesNotContain("FILES");
    }
}
