package nct.point.mapper;

import org.apache.ibatis.annotations.Mapper;

import nct.point.domain.SystemSetting;

/**
 * Claude Code 작성 (BJN, 2026-07-16)
 *
 * [시스템 설정 - MyBatis 매퍼]
 * - SQL 본문은 resources/mapper/point/SystemSettingMapper.xml
 */
@Mapper
public interface SystemSettingMapper {

    /** 충전 한도 조회 (SYSTEM_SETTING은 단일 행이라 조건 없이 1건만 조회) */
    SystemSetting selectChargeLimits();
}
