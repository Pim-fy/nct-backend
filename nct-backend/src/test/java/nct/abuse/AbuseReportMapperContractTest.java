package nct.abuse;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 담당자 7 · F-OPS-007: 관리자 신고 응답의 공통코드명 조회 계약을 검증합니다. */
class AbuseReportMapperContractTest {

    @Test
    void selectsUnifiedReportAndTradeContextForAdminResponses() throws IOException {
        String mapper = new ClassPathResource("mapper/abuse/AbuseReportMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ")
                .trim();
        String columns = mapper.substring(
                mapper.indexOf("<sql id=\"adminReportColumns\">"),
                mapper.indexOf("</sql>", mapper.indexOf("<sql id=\"adminReportColumns\">")));
        String joins = mapper.substring(
                mapper.indexOf("<sql id=\"adminReportJoins\">"),
                mapper.indexOf("</sql>", mapper.indexOf("<sql id=\"adminReportJoins\">")));

        assertThat(columns)
                .contains("reportType.CMM_NM AS reportTypeName")
                .contains("WHEN rt.ABR_SN IS NOT NULL THEN 'TRADE_ISSUE'")
                .contains("rt.TRD_SN AS tradeSn")
                .contains("rt.ABR_TRD_RSLT_CD AS tradeResultCode")
                .doesNotContain("linkedDispute");
        assertThat(joins)
                .contains("LEFT JOIN ABUSE_REPORT_TRADE rt ON rt.ABR_SN = ar.ABR_SN")
                .contains("LEFT JOIN TRADE t ON t.TRD_SN = rt.TRD_SN")
                .contains("LEFT JOIN CMM_CODE reportType ON reportType.CMM_CD = ar.ABR_TYPE_CD");
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
