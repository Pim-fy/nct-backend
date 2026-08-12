package nct.abuse;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 담당자 7 · F-OPS-007: 관리자 신고 응답의 공통코드명 조회 계약을 검증합니다. */
class AbuseReportMapperContractTest {

    @Test
    void selectsDynamicReportTypeNameForAdminResponses() throws IOException {
        String mapper = new ClassPathResource("mapper/abuse/AbuseReportMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ")
                .trim();
        String columns = mapper.substring(
                mapper.indexOf("<sql id=\"adminReportColumns\">"),
                mapper.indexOf("</sql>", mapper.indexOf("<sql id=\"adminReportColumns\">")));

        assertThat(columns)
                .contains("FROM CMM_CODE")
                .contains("WHERE CMM_CD = ABR_TYPE_CD")
                .contains("AS reportTypeName")
                .contains("WHERE d.ABR_SN = ABUSE_REPORT.ABR_SN")
                .contains("AS linkedDisputeSn")
                .contains("AS linkedDisputeStatusCode")
                .contains("AS linkedDisputeResultCode");
    }

    @Test
    void selectsInternalReferenceFieldsForMyReportTargetEnrichment() throws IOException {
        String mapper = new ClassPathResource("mapper/abuse/AbuseReportMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ")
                .trim();
        String columns = mapper.substring(
                mapper.indexOf("<sql id=\"myReportColumns\">") ,
                mapper.indexOf("</sql>", mapper.indexOf("<sql id=\"myReportColumns\">")));

        assertThat(columns)
                .contains("ar.ABR_REF_TYPE_CD AS referenceTypeCode")
                .contains("ar.ABR_REF_SN AS referenceSn");
    }
}
