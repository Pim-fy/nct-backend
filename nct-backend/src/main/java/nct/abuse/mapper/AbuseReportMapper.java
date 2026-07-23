package nct.abuse.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.abuse.domain.AbuseReport;
import nct.abuse.dto.AdminAbuseReportResponse;

@Mapper
public interface AbuseReportMapper {

    int insertAutomaticReport(AbuseReport report);

    Long findReportIdByRiskEventIdForUpdate(@Param("riskEventSn") Long riskEventSn);

    AbuseReport findReportByIdForUpdate(@Param("reportSn") Long reportSn);

    List<AdminAbuseReportResponse> findPendingReports(
            @Param("receivedStatusCode") String receivedStatusCode,
            @Param("processingStatusCode") String processingStatusCode);

    AdminAbuseReportResponse findReportDetailById(@Param("reportSn") Long reportSn);

    int updateDecision(
            @Param("reportSn") Long reportSn,
            @Param("expectedStatusCode") String expectedStatusCode,
            @Param("newStatusCode") String newStatusCode,
            @Param("processReason") String processReason,
            @Param("actorId") String actorId);
}
