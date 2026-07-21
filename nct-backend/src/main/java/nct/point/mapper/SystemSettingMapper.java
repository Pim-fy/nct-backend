package nct.point.mapper;

import org.apache.ibatis.annotations.Mapper;

import nct.point.domain.AuctionPolicy;
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

    /** 경매 정책 조회 (담당자5 소비용, 2026-07-21) — 행이 없으면 null 반환, 값 검증은 호출부(PointService) 책임 */
    AuctionPolicy selectAuctionPolicy();
}
