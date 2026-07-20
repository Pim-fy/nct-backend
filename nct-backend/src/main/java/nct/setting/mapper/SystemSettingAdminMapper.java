package nct.setting.mapper;

import org.apache.ibatis.annotations.Mapper;

import nct.setting.domain.SystemSettingDetail;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [시스템 설정 - 관리자용 MyBatis 매퍼] (F-OPS-024)
 * - SQL 본문은 resources/mapper/setting/SystemSettingAdminMapper.xml
 */
@Mapper
public interface SystemSettingAdminMapper {

    /** 전체 설정 조회 (단일 행이라 조건 없이 1건) */
    SystemSettingDetail selectOne();

    /** 수정 검증~UPDATE 사이 동시 수정 경합을 막기 위한 행 잠금 조회 */
    SystemSettingDetail selectOneForUpdate();

    /** 전체 설정 갱신 — 단일 행을 앱 계층에서 UPDATE (INSERT/DELETE 금지, F-OPS-024) */
    int update(SystemSettingDetail setting);
}
