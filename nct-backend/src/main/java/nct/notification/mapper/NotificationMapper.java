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

    /** 이메일 발송 상태 갱신 (F-COM-006) — 발송 시도 결과(성공/실패)를 알림 행에 기록 */
    int updateEmailStatus(@Param("ntfSn") long ntfSn, @Param("statusCd") String statusCd);

    /** 수신자 이메일 주소 (F-COM-006) — USERS는 타 담당자 소유, 읽기 전용 조회만 */
    String selectUserEmail(@Param("usrSn") long usrSn);
}
