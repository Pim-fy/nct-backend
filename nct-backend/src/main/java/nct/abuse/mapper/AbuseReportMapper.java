package nct.abuse.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.abuse.domain.AbuseReport;
import nct.abuse.dto.AdminAbuseReportResponse;
import nct.abuse.dto.ManualAbuseReportStatusResponse;
import nct.abuse.dto.MyAbuseReportResponse;

@Mapper
public interface AbuseReportMapper {

    int insertAutomaticReport(AbuseReport report);

    int insertManualReport(AbuseReport report);

    Long findManualReportId(
            @Param("reporterUserSn") Long reporterUserSn,
            @Param("referenceTypeCode") String referenceTypeCode,
            @Param("referenceSn") Long referenceSn);

    List<ManualAbuseReportStatusResponse> findManualReportsByReporterAndReferenceType(
            @Param("reporterUserSn") Long reporterUserSn,
            @Param("referenceTypeCode") String referenceTypeCode);

    List<ManualAbuseReportStatusResponse> findActiveManualReportsByReferences(
            @Param("referenceTypeCode") String referenceTypeCode,
            @Param("referenceSns") List<Long> referenceSns,
            @Param("receivedStatusCode") String receivedStatusCode,
            @Param("processingStatusCode") String processingStatusCode);

    Long findReportIdByRiskEventIdForUpdate(@Param("riskEventSn") Long riskEventSn);

    AbuseReport findReportByIdForUpdate(@Param("reportSn") Long reportSn);

    List<AdminAbuseReportResponse> findPendingReports(
            @Param("receivedStatusCode") String receivedStatusCode,
            @Param("processingStatusCode") String processingStatusCode);

    List<AdminAbuseReportResponse> findAdminReports(
            @Param("statusCode") String statusCode,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("size") int size);

    long countAdminReports(
            @Param("statusCode") String statusCode,
            @Param("keyword") String keyword);

    AdminAbuseReportResponse findReportDetailById(@Param("reportSn") Long reportSn);

    int insertCustomerReport(AbuseReport report);

    List<MyAbuseReportResponse> findMyReports(
            @Param("reporterUserSn") Long reporterUserSn,
            @Param("statusCode") String statusCode,
            @Param("offset") int offset,
            @Param("size") int size);

    int countMyReports(
            @Param("reporterUserSn") Long reporterUserSn,
            @Param("statusCode") String statusCode);

    MyAbuseReportResponse findMyReportById(
            @Param("reportSn") Long reportSn,
            @Param("reporterUserSn") Long reporterUserSn);

    int updateDecision(
            @Param("reportSn") Long reportSn,
            @Param("expectedStatusCode") String expectedStatusCode,
            @Param("newStatusCode") String newStatusCode,
            @Param("processReason") String processReason,
            @Param("actorId") String actorId);
}
