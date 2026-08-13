package nct.ops.sanction.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.ops.sanction.domain.SanctionImpactRecord;

/** 담당자 7 · 신고 처리 제재: 제재 영향 항목의 저장·복구 상태를 관리합니다. */
@Mapper
public interface SanctionImpactMapper {

    int insert(SanctionImpactRecord impact);

    List<SanctionImpactRecord> findBySanctionForUpdate(@Param("sanctionSn") Long sanctionSn);

    List<SanctionImpactRecord> findByReport(@Param("reportSn") Long reportSn);

    int countOtherActiveBlockingImpacts(
            @Param("sanctionSn") Long sanctionSn,
            @Param("referenceTypeCode") String referenceTypeCode,
            @Param("referenceSn") Long referenceSn);

    List<SanctionImpactRecord> findUnresolvedByReferenceForUpdate(
            @Param("referenceTypeCode") String referenceTypeCode,
            @Param("referenceSn") Long referenceSn);

    List<SanctionImpactRecord> findUnresolvedTemporaryByUserForUpdate(
            @Param("userSn") Long userSn);

    int updateStatus(
            @Param("impactSn") Long impactSn,
            @Param("expectedStatusCode") String expectedStatusCode,
            @Param("statusCode") String statusCode,
            @Param("result") String result,
            @Param("actorId") String actorId);

}
