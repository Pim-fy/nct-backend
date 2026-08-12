package nct.abuse.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.abuse.domain.AbuseReport;
import nct.abuse.dto.AbuseReportFileResponse;
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

    boolean existsOtherActiveReportLinkedToTrade(
            @Param("tradeSn") Long tradeSn,
            @Param("excludedReportSn") Long excludedReportSn,
            @Param("receivedStatusCode") String receivedStatusCode,
            @Param("processingStatusCode") String processingStatusCode);

    boolean existsOtherActiveReportLinkedToAuction(
            @Param("auctionSn") Long auctionSn,
            @Param("excludedReportSn") Long excludedReportSn,
            @Param("receivedStatusCode") String receivedStatusCode,
            @Param("processingStatusCode") String processingStatusCode);

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

    /** 담당자 7 · F-OPS-002: 회원 상세 화면에 표시할 신고 이력입니다. */
    List<AdminAbuseReportResponse> findReportsByReportedUser(
            @Param("reportedUserSn") Long reportedUserSn,
            @Param("limit") int limit);

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

    /** 담당자 7 · F-COM-018: 같은 신고자·대상·유형의 미처리 중복 신고를 찾습니다. */
    Long findActiveCustomerReportId(
            @Param("reporterUserSn") Long reporterUserSn,
            @Param("reportedUserSn") Long reportedUserSn,
            @Param("reportTypeCode") String reportTypeCode,
            @Param("referenceTypeCode") String referenceTypeCode,
            @Param("referenceSn") Long referenceSn,
            @Param("receivedStatusCode") String receivedStatusCode,
            @Param("processingStatusCode") String processingStatusCode);

    /** 담당자 7 · F-COM-018: 업로드된 FILES 행을 신고에 순서대로 연결합니다. */
    int insertReportFile(
            @Param("reportSn") Long reportSn,
            @Param("fileSn") Long fileSn,
            @Param("sortNo") int sortNo,
            @Param("actorId") String actorId);

    List<AbuseReportFileResponse> findReportFiles(@Param("reportSn") Long reportSn);

    int countReportFileLink(
            @Param("reportSn") Long reportSn,
            @Param("fileSn") Long fileSn);

    int updateDecision(
            @Param("reportSn") Long reportSn,
            @Param("expectedStatusCode") String expectedStatusCode,
            @Param("newStatusCode") String newStatusCode,
            @Param("processReason") String processReason,
            @Param("actorId") String actorId,
            @Param("requestId") String requestId);
}
