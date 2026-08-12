package nct.ops.sanction.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.ops.sanction.domain.SanctionRecord;

/** @ai_generated F-AUTH-012 SANCTION의 현재 유효 제재 여부만 제공하는 담당자5 읽기 Mapper다. */
@Mapper
public interface SanctionMapper {

    /** 시작 시각이 지났고 종료되지 않은 제재가 하나라도 있으면 true다. */
    boolean existsActiveSanction(@Param("userSn") Long userSn);

    /** 제재 명령을 직렬화하기 위해 회원 행을 잠그고, 존재하지 않으면 null을 반환한다. */
    Long lockUser(@Param("userSn") Long userSn);

    SanctionRecord findByRestrictRequestId(@Param("requestId") String requestId);

    SanctionRecord findByReleaseRequestId(@Param("requestId") String requestId);

    SanctionRecord findBySourceReportForUpdate(@Param("reportSn") Long reportSn);

    SanctionRecord findBySourceReport(@Param("reportSn") Long reportSn);

    SanctionRecord findBySanctionIdForUpdate(@Param("sanctionSn") Long sanctionSn);

    /** 계정 정지(SNCC0003) 중 현재 유효한 행을 모두 잠가 조회한다. */
    List<SanctionRecord> findActiveAccountSuspensionsForUpdate(@Param("userSn") Long userSn);

    int insertAccountSuspension(
            @Param("userSn") Long userSn,
            @Param("adminUserSn") Long adminUserSn,
            @Param("reason") String reason,
            @Param("requestId") String requestId,
            @Param("actorId") String actorId);

    int insertReportAccountSuspension(SanctionRecord sanction);

    int releaseAccountSuspension(
            @Param("sanctionSn") Long sanctionSn,
            @Param("releaseRequestId") String releaseRequestId,
            @Param("actorId") String actorId);

    int releaseTemporaryReportSanction(
            @Param("sanctionSn") Long sanctionSn,
            @Param("releaseRequestId") String releaseRequestId,
            @Param("actorId") String actorId,
            @Param("automatic") boolean automatic);

    List<Long> findExpiredUnprocessedReportSanctionIds(@Param("limit") int limit);

    int countOtherActiveAccountSuspensions(
            @Param("userSn") Long userSn,
            @Param("excludeSanctionSn") Long excludeSanctionSn);

    boolean existsActiveReportSanction(@Param("userSn") Long userSn);

    boolean existsActiveAccountSuspension(@Param("userSn") Long userSn);

    List<SanctionRecord> findHistory(
            @Param("userSn") Long userSn,
            @Param("limit") int limit);
}
