package nct.notification.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.notification.domain.UserNotificationEventSetting;

/**
 * Claude Code 작성 (BJN, 2026-07-24)
 *
 * [알림 이벤트별 설정 - MyBatis 매퍼] (F-COM-012 세분화)
 * - SQL 본문은 resources/mapper/notification/UserNotificationEventSettingMapper.xml
 */
@Mapper
public interface UserNotificationEventSettingMapper {

    /** 내 이벤트별 설정 전체 조회 — 저장한 적 없는 이벤트는 행 자체가 없다(서비스에서 기본값 채움) */
    List<UserNotificationEventSetting> selectByUser(@Param("usrSn") long usrSn);

    /** 특정 이벤트 1건 조회 — notify 발행 시 게이팅 확인용. 행 없으면 null(기본값 Y로 간주) */
    UserNotificationEventSetting selectByUserAndEvent(@Param("usrSn") long usrSn, @Param("ntfEvtCd") String ntfEvtCd);

    /** 저장 — 회원×이벤트 1행(UNIQUE USR_SN, NTF_EVT_CD)이라 없으면 INSERT, 있으면 UPDATE (업서트) */
    int upsert(UserNotificationEventSetting setting);
}
