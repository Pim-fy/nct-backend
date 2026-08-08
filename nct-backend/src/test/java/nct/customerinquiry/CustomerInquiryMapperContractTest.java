package nct.customerinquiry;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 담당자 7 · 고객 문의 Mapper의 소유권, 정렬, 조건부 상태 변경 SQL 계약을 검증한다. */
class CustomerInquiryMapperContractTest {

    private static final String MAPPER = "mapper/customerinquiry/CustomerInquiryMapper.xml";

    @Test
    void userQueriesAlwaysRestrictInquiryOwnership() throws IOException {
        assertThat(element("select", "findMyInquiries"))
                .contains("inquiry.USR_SN = #{userSn}")
                .contains("inquiry.CST_INQ_USE_YN = 'Y'")
                .contains("ORDER BY inquiry.CST_INQ_REG_DT DESC, inquiry.CST_INQ_SN DESC");
        assertThat(element("select", "findMyInquiryDetail"))
                .contains("inquiry.CST_INQ_SN = #{inquirySn}")
                .contains("inquiry.USR_SN = #{userSn}")
                .contains("inquiry.CST_INQ_USE_YN = 'Y'");
    }

    @Test
    void adminPageUsesFiltersAndOperationalStatusOrder() throws IOException {
        String select = element("select", "findAdminInquiries");
        assertThat(select)
                .contains("inquiry.CST_INQ_STATUS_CD = #{statusCode}")
                .contains("inquiry.CST_INQ_TYPE_CD = #{inquiryTypeCode}")
                .contains("CAST(inquiry.CST_INQ_SN AS CHAR) LIKE CONCAT('%', #{keyword}, '%')")
                .contains("CASE inquiry.CST_INQ_STATUS_CD WHEN 'INQC0007' THEN 0 "
                        + "WHEN 'INQC0008' THEN 1 WHEN 'INQC0009' THEN 2 ELSE 3 END ASC, "
                        + "inquiry.CST_INQ_REG_DT DESC, inquiry.CST_INQ_SN DESC")
                .contains("LIMIT #{size} OFFSET #{offset}");
        assertThat(element("select", "countAdminInquiries"))
                .contains("inquiry.CST_INQ_STATUS_CD = #{statusCode}")
                .contains("inquiry.CST_INQ_TYPE_CD = #{inquiryTypeCode}")
                .contains("inquiry.CST_INQ_TTL LIKE CONCAT('%', #{keyword}, '%')");
    }

    @Test
    void stateUpdatesAreSingleConditionalStatements() throws IOException {
        assertThat(element("update", "startProcessing"))
                .contains("CST_INQ_STATUS_CD = #{receivedStatusCode}")
                .contains("CST_INQ_PROC_USR_SN IS NULL")
                .contains("CST_INQ_ANS_CN IS NULL")
                .contains("CST_INQ_ANS_DT IS NULL")
                .contains("CST_INQ_USE_YN = 'Y'");
        assertThat(element("update", "completeAnswer"))
                .contains("CST_INQ_STATUS_CD = #{processingStatusCode}")
                .contains("CST_INQ_PROC_USR_SN = #{adminUserSn}")
                .contains("CST_INQ_ANS_CN IS NULL")
                .contains("CST_INQ_ANS_DT IS NULL")
                .contains("CST_INQ_USE_YN = 'Y'");
    }

    @Test
    void detailColumnOrderMatchesRecordConstructors() throws IOException {
        assertThat(element("select", "findAdminInquiryDetail"))
                .contains("inquiry.CST_INQ_ANS_CN AS answer, "
                        + "inquiry.CST_INQ_REG_DT AS registeredAt, "
                        + "inquiry.CST_INQ_UPDT_DT AS updatedAt, "
                        + "inquiry.CST_INQ_ANS_DT AS answeredAt");
        assertThat(element("select", "findMyInquiryDetail"))
                .contains("inquiry.CST_INQ_ANS_CN AS answer, "
                        + "inquiry.CST_INQ_REG_DT AS registeredAt, "
                        + "inquiry.CST_INQ_UPDT_DT AS updatedAt, "
                        + "inquiry.CST_INQ_ANS_DT AS answeredAt");
    }

    private String element(String name, String id) throws IOException {
        ClassPathResource resource = new ClassPathResource(MAPPER);
        try (var input = resource.getInputStream()) {
            String normalized = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ");
            String startMarker = "<" + name + " id=\"" + id + "\"";
            int start = normalized.indexOf(startMarker);
            int end = normalized.indexOf("</" + name + ">", start);
            assertThat(start).as("%s %s exists", name, id).isGreaterThanOrEqualTo(0);
            assertThat(end).as("%s %s is closed", name, id).isGreaterThan(start);
            return normalized.substring(start, end);
        }
    }
}
