package nct.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 담당자 7 연동 · F-PROV-009: 제공자 견적 요약이 상태별 집계 계약을 지키는지 확인합니다. */
class MyQuoteSummaryMapperContractTest {

    @Test
    void summaryAggregatesAllStatusCountsInOneSelect() throws IOException {
        String summary = loadNormalizedSelect("findMyQuoteSummary");

        assertThat(summary)
                .contains("COUNT(*) AS totalQuoteCount")
                .contains("QUT_STATUS_CD IN ('QUTC0001', 'QUTC0002') THEN 1 END) AS activeQuoteCount")
                .contains("QUT_STATUS_CD = 'QUTC0004' THEN 1 END) AS selectedQuoteCount")
                .contains("QUT_STATUS_CD IN ('QUTC0003', 'QUTC0005') THEN 1 END) AS endedQuoteCount")
                .contains("('QUTC0001', 'QUTC0002', 'QUTC0003', 'QUTC0004', 'QUTC0005')")
                .contains("THEN 1 END) AS unsupportedQuoteCount")
                .contains("FROM QUOTE")
                .contains("WHERE USR_SN = #{usrSn}");
    }

    private String loadNormalizedSelect(String selectId) throws IOException {
        ClassPathResource mapperResource = new ClassPathResource("mapper/quote/QuoteMapper.xml");
        try (var inputStream = mapperResource.getInputStream()) {
            String mapperXml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ");
            int elementStart = mapperXml.indexOf("<select id=\"" + selectId + "\"");
            int elementEnd = mapperXml.indexOf("</select>", elementStart);
            assertThat(elementStart).isGreaterThanOrEqualTo(0);
            assertThat(elementEnd).isGreaterThan(elementStart);
            return mapperXml.substring(elementStart, elementEnd);
        }
    }
}
