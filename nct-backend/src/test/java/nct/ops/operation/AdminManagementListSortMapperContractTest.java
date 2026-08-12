package nct.ops.operation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

// 담당자 7: 관리자 목록의 업무 상태 우선순위와 상태별 최신순 SQL 계약을 검증한다.
class AdminManagementListSortMapperContractTest {

    @Test
    @DisplayName("경매 관리는 경매·거래 상태 순으로 묶고 같은 상태는 최신순이다")
    void auctionsUseOperationalPriorityAndLatestOrder() throws IOException {
        String fromWhere = loadNormalizedSql(
                "mapper/ops/operation/AdminAuctionQueryMapper.xml",
                "fromWhere");
        String count = loadNormalizedSelect(
                "mapper/ops/operation/AdminAuctionQueryMapper.xml",
                "count");
        String select = loadNormalizedSelect(
                "mapper/ops/operation/AdminAuctionQueryMapper.xml",
                "findPage");

        assertThat(fromWhere)
                .contains("WHERE r.AUC_SN = a.AUC_SN")
                .contains("ORDER BY r.AUC_CNL_REQ_REG_DT DESC, r.AUC_CNL_REQ_SN DESC LIMIT 1")
                .contains("AND cancel_request.AUC_CNL_REQ_APRV_YN IS NULL")
                .contains("OR cancel_request.AUC_CNL_REQ_APRV_YN IS NOT NULL");
        assertThat(count).contains("<include refid=\"fromWhere\"/>");
        assertThat(select)
                .contains("<include refid=\"fromWhere\"/>")
                .contains("CASE a.AUC_STATUS_CD WHEN 'AUCC0006' THEN 0 "
                        + "WHEN 'AUCC0002' THEN 1 WHEN 'AUCC0001' THEN 2 "
                        + "WHEN 'AUCC0003' THEN 3 WHEN 'AUCC0004' THEN 4 "
                        + "WHEN 'AUCC0005' THEN 5 ELSE 6 END ASC, "
                        + "CASE trade.TRD_STATUS_CD WHEN 'TRDC0003' THEN 0 "
                        + "WHEN 'TRDC0004' THEN 1 WHEN 'TRDC0005' THEN 2 "
                        + "WHEN 'TRDC0006' THEN 3 WHEN 'TRDC0007' THEN 4 "
                        + "WHEN 'TRDC0008' THEN 5 ELSE 6 END ASC, "
                        + "CASE WHEN a.AUC_STATUS_CD = 'AUCC0006' "
                        + "THEN cancel_request.AUC_CNL_REQ_REG_DT END DESC, "
                        + "a.AUC_REG_DT DESC, a.AUC_SN DESC");
        assertOrderBeforeLimit(select);
    }

    @Test
    @DisplayName("제공자 심사는 대기, 승인, 반려 순이며 같은 상태는 최신 신청순이다")
    void providerApplicationsUseReviewPriorityAndLatestOrder() throws IOException {
        assertThat(loadNormalizedSelect("mapper/provider/ProviderApplicationMapper.xml", "findForAdmin"))
                .contains("a.PRV_APLY_STATUS_CD IN ('PRVC0002', 'PRVC0003', 'PRVC0004')")
                .contains("CASE a.PRV_APLY_STATUS_CD WHEN 'PRVC0002' THEN 0 "
                        + "WHEN 'PRVC0003' THEN 1 WHEN 'PRVC0004' THEN 2 ELSE 3 END ASC, "
                        + "a.PRV_APLY_REG_DT DESC, a.PRV_APLY_SN DESC");
    }

    @Test
    @DisplayName("서비스 요청은 공개, 임시저장, 매칭완료, 종료 순이며 같은 상태는 최신 등록순이다")
    void serviceRequestsUseOperationalPriorityAndLatestOrder() throws IOException {
        String select = loadNormalizedSelect(
                "mapper/servicerequest/ServiceRequestMapper.xml",
                "findAdminServiceRequestPage");

        assertThat(select)
                .contains("CASE s.SVC_REQ_STATUS_CD WHEN 'SVCC0002' THEN 0 "
                        + "WHEN 'SVCC0001' THEN 1 WHEN 'SVCC0003' THEN 2 "
                        + "WHEN 'SVCC0004' THEN 3 ELSE 4 END ASC, "
                        + "s.SVC_REQ_REG_DT DESC, s.SVC_REQ_SN DESC");
        assertOrderBeforeLimit(select);
    }

    @Test
    @DisplayName("환전 관리는 신청, 완료, 반려 순이며 같은 상태는 최신 신청순이다")
    void exchangeOrdersUseRequestPriorityAndLatestOrder() throws IOException {
        String select = loadNormalizedSelect("mapper/point/PointExchangeOrderMapper.xml", "selectAdminList");

        assertThat(select)
                .contains("CASE O.PT_EXC_ORD_STATUS_CD WHEN 'PEOC0001' THEN 0 "
                        + "WHEN 'PEOC0002' THEN 1 WHEN 'PEOC0003' THEN 2 ELSE 3 END ASC, "
                        + "O.PT_EXC_ORD_REG_DT DESC, O.PT_EXC_ORD_SN DESC");
        assertOrderBeforeLimit(select);
    }

    @Test
    @DisplayName("신고 관리는 접수, 처리중, 완료, 반려 순이며 같은 상태는 최신 접수순이다")
    void reportsUseReceivedPriorityAndLatestOrder() throws IOException {
        String select = loadNormalizedSelect("mapper/abuse/AbuseReportMapper.xml", "findAdminReports");

        assertThat(select)
                .contains("CASE ABR_STATUS_CD WHEN 'ABSC0001' THEN 0 "
                        + "WHEN 'ABSC0002' THEN 1 WHEN 'ABSC0003' THEN 2 "
                        + "WHEN 'ABSC0004' THEN 3 ELSE 4 END ASC, "
                        + "ABR_REG_DT DESC, ABR_SN DESC");
        assertOrderBeforeLimit(select);
    }

    @Test
    @DisplayName("관리자 신고 조회는 신고 제목과 대상명을 반환하고 검색한다")
    void reportsExposeAndSearchCustomerReportContext() throws IOException {
        String columns = loadNormalizedSql("mapper/abuse/AbuseReportMapper.xml", "adminReportColumns");
        String searchWhere = loadNormalizedSql("mapper/abuse/AbuseReportMapper.xml", "adminReportSearchWhere");

        assertThat(columns)
                .contains("ABR_TITLE_NM AS title")
                .contains("ABR_TRG_NM AS targetName");
        assertThat(searchWhere)
                .contains("ABR_TITLE_NM LIKE CONCAT('%', #{keyword}, '%')")
                .contains("ABR_TRG_NM LIKE CONCAT('%', #{keyword}, '%')");
    }

    private void assertOrderBeforeLimit(String select) {
        assertThat(select.indexOf("ORDER BY"))
                .isGreaterThanOrEqualTo(0)
                .isLessThan(select.indexOf("LIMIT"));
    }

    private String loadNormalizedSelect(String classpathLocation, String selectId) throws IOException {
        return loadNormalizedElement(classpathLocation, "select", selectId);
    }

    private String loadNormalizedSql(String classpathLocation, String sqlId) throws IOException {
        return loadNormalizedElement(classpathLocation, "sql", sqlId);
    }

    private String loadNormalizedElement(String classpathLocation, String elementName, String elementId)
            throws IOException {
        ClassPathResource mapperResource = new ClassPathResource(classpathLocation);

        try (var inputStream = mapperResource.getInputStream()) {
            String normalizedXml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ");
            String elementStartMarker = "<" + elementName + " id=\"" + elementId + "\"";
            int elementStart = normalizedXml.indexOf(elementStartMarker);
            int elementEnd = normalizedXml.indexOf("</" + elementName + ">", elementStart);

            assertThat(elementStart)
                    .as("Mapper %s %s exists", elementName, elementId)
                    .isGreaterThanOrEqualTo(0);
            assertThat(elementEnd)
                    .as("Mapper %s %s is closed", elementName, elementId)
                    .isGreaterThan(elementStart);
            return normalizedXml.substring(elementStart, elementEnd);
        }
    }
}
