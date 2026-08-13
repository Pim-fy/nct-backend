package nct.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** 담당자 7 통합 · F-SVC-003·005: 활성 견적 중복과 요청 종료 상태 전이 SQL 계약을 검증합니다. */
class QuoteLifecycleMapperContractTest {

    @Test
    void duplicateCheckAndExpirationUseOnlyActiveQuotes() throws IOException {
        String mapper = new String(
                getClass().getResourceAsStream("/mapper/quote/QuoteMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8).replaceAll("\\s+", " ");

        String duplicateCheck = element(mapper, "select", "countActiveQuotesByRequestAndProvider");
        assertThat(duplicateCheck)
                .contains("SVC_REQ_SN = #{svcReqSn}")
                .contains("USR_SN = #{usrSn}")
                .contains("QUT_STATUS_CD IN ('QUTC0001', 'QUTC0002')");

        String expiration = element(mapper, "update", "expireActiveQuotesByServiceRequestId");
        assertThat(expiration)
                .contains("QUT_STATUS_CD = 'QUTC0003'")
                .contains("QUT_STATUS_CD IN ('QUTC0001', 'QUTC0002')")
                .contains("NOT EXISTS")
                .contains("FROM TRADE");

        String adminInvalidation = element(mapper, "update", "adminInvalidateActiveQuote");
        assertThat(adminInvalidation)
                .contains("QUT_STATUS_CD IN ('QUTC0001', 'QUTC0002')")
                .contains("NOT EXISTS")
                .contains("FROM TRADE");
    }

    @Test
    void quoteSubmissionAndAutomaticCloseUseTheSameDeadlineBoundary() throws IOException {
        String mapper = new String(
                getClass().getResourceAsStream(
                        "/mapper/servicerequest/ServiceRequestMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8).replaceAll("\\s+", " ");

        String submissionWindow = element(
                mapper,
                "select",
                "countOpenQuoteSubmissionWindow");
        assertThat(submissionWindow)
                .contains("SVC_REQ_DEADLINE_DT")
                .contains("INTERVAL 5 DAY")
                .contains("SVC_REQ_FIELD_TYPE_CD = 'CALENDAR'")
                .contains(") &gt; NOW()");

        String automaticClose = element(mapper, "update", "autoCloseServiceRequest");
        assertThat(automaticClose)
                .contains("SVC_REQ_DEADLINE_DT")
                .contains("INTERVAL 5 DAY")
                .contains("SVC_REQ_FIELD_TYPE_CD = 'CALENDAR'")
                .contains(") &lt;= NOW()");
    }

    private String element(String mapper, String tag, String id) {
        int start = mapper.indexOf("<" + tag + " id=\"" + id + "\"");
        int end = mapper.indexOf("</" + tag + ">", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return mapper.substring(start, end);
    }
}
