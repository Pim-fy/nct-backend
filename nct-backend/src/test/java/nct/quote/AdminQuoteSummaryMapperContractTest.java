package nct.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** 담당자 7 · F-OPS-021: 관리자 견적 요약이 요청 목록을 일괄 조회하는지 확인합니다. */
class AdminQuoteSummaryMapperContractTest {

    @Test
    void summaryQueryUsesOneInClauseAndDetectsSelectedDuplicates() throws IOException {
        String mapper = new String(
                getClass().getResourceAsStream("/mapper/quote/QuoteMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(mapper)
                .contains("<select id=\"findAdminSummaries\"")
                .contains("collection=\"serviceRequestIds\"")
                .contains("AS selectedQuoteCount")
                .contains("AS unsupportedQuoteCount")
                .contains("GROUP BY SVC_REQ_SN");
    }
}
