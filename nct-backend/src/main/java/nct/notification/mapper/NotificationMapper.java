package nct.notification.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.notification.domain.Notification;

/**
 * [알림 - MyBatis 매퍼]
 * - SQL 본문은 resources/mapper/notification/NotificationMapper.xml
 */
@Mapper
public interface NotificationMapper {

    /** 알림 행 추가 */
    int insert(Notification notification);

    /** 내 알림 목록 (공통코드 한글명 조인 포함, 최신순 100건) */
    List<Notification> selectListByUser(@Param("usrSn") long usrSn);

    /** 미읽음 개수 */
    int countUnread(@Param("usrSn") long usrSn);

    /** 개별 읽음 처리 — usrSn 조건이 함께 걸려 있어 타인의 알림은 건드릴 수 없다 */
    int markRead(@Param("ntfSn") long ntfSn, @Param("usrSn") long usrSn);

    /** 전체 읽음 처리 */
    int markAllRead(@Param("usrSn") long usrSn);
}
