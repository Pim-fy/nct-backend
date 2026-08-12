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

    int updateDecision(
            @Param("reportSn") Long reportSn,
            @Param("expectedStatusCode") String expectedStatusCode,
            @Param("newStatusCode") String newStatusCode,
            @Param("processReason") String processReason,
            @Param("actorId") String actorId);

    // @ai_generated (담당자1 황희준, 2026-08-12, 조율 대기): F-AUTH-011/POL-AUTH-013 - 탈퇴 전
    // 하드 차단용. 본인이 신고자 또는 피신고자인 접수·처리중(ABRC0005·0006) 신고 건수를 센다.
    int countOpenReportsByUser(@Param("userSn") Long userSn);
}
