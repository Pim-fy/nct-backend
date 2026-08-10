package nct.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** 담당자 7 연동 · F-PROV-009: 활성 견적 집계가 견적 도메인의 상태 기준을 지키는지 확인합니다. */
class MyQuoteSummaryMapperContractTest {

    @Test
    void activeSummaryCountsSubmittedAndRevisedQuotesOnly() throws IOException {
        String mapper = new String(
                getClass().getResourceAsStream("/mapper/quote/QuoteMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(mapper)
                .contains("<select id=\"countMyActiveQuotes\"")
                .contains("QUT_STATUS_CD IN ('QUTC0001', 'QUTC0002')");
    }
}
