package nct.notification.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.notification.domain.UserNotificationSetting;

/**
 * Claude Code 작성 (BJN, 2026-07-16)
 *
 * [알림 설정 - MyBatis 매퍼] (F-COM-012)
 * - SQL 본문은 resources/mapper/notification/UserNotificationSettingMapper.xml
 */
@Mapper
public interface UserNotificationSettingMapper {

    /** 내 알림 설정 조회 — 행이 없으면 null (서비스에서 기본값으로 대체) */
    UserNotificationSetting selectByUser(@Param("usrSn") long usrSn);

    /** 저장 — 회원당 1행(UNIQUE USR_SN)이라 없으면 INSERT, 있으면 UPDATE (업서트) */
    int upsert(UserNotificationSetting setting);
}
