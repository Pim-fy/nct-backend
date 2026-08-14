package nct.abuse.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.abuse.domain.ReportImpactRecord;

/** 담당자 7 · F-OPS-007: 신고 단건 보류 영향 기록 Mapper입니다. */
@Mapper
public interface ReportImpactMapper {

    ReportImpactRecord findActiveBaselineForUpdate(
            @Param("referenceTypeCode") String referenceTypeCode,
            @Param("referenceSn") Long referenceSn,
            @Param("receivedStatusCode") String receivedStatusCode,
            @Param("processingStatusCode") String processingStatusCode);

    ReportImpactRecord findByReportForUpdate(@Param("reportSn") Long reportSn);

    int insert(ReportImpactRecord impact);

    boolean existsOtherActiveImpact(
            @Param("referenceTypeCode") String referenceTypeCode,
            @Param("referenceSn") Long referenceSn,
            @Param("excludedReportSn") Long excludedReportSn,
            @Param("receivedStatusCode") String receivedStatusCode,
            @Param("processingStatusCode") String processingStatusCode);

    int updateResult(
            @Param("impactSn") Long impactSn,
            @Param("expectedStatusCode") String expectedStatusCode,
            @Param("statusCode") String statusCode,
            @Param("result") String result,
            @Param("actorId") String actorId);
}
