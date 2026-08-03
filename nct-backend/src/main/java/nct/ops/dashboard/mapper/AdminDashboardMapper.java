package nct.ops.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;

/** 담당자 7 · F-OPS-010: 대시보드 조치 필요 건수만 읽는 집계 매퍼입니다. */
@Mapper
public interface AdminDashboardMapper {

    long countPendingProviderApplications();

    long countPendingReports();
}
