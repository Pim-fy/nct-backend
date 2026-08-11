package nct.quote.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 담당자 7 · F-PROV-009: 기존 활성 견적 수를 유지한 제공자 견적 요약 응답 계약입니다. */
class MyQuoteSummaryResponseTest {

    @Test
    void exposesTotalActiveSelectedAndEndedCounts() {
        MyQuoteSummaryResponse response = new MyQuoteSummaryResponse(8, 2, 1, 5);

        assertThat(response.totalQuoteCount()).isEqualTo(8);
        assertThat(response.activeQuoteCount()).isEqualTo(2);
        assertThat(response.selectedQuoteCount()).isEqualTo(1);
        assertThat(response.endedQuoteCount()).isEqualTo(5);
    }
}
