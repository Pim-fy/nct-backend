// Claude Code 작성 (BJN, 2026-07-19)
package nct.notification.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * [알림 - 관리자 알림함 집계 매퍼] (담당자6, 03_관리자/20_notification.html)
 * - SQL 본문은 resources/mapper/notification/AdminNotificationMapper.xml
 *
 * ⚠️ 여기서 조회하는 USERS·PROVIDER_APPLY·ABUSE_REPORT·AUCTION·SERVICE_REQUEST는
 * 전부 타 담당자 소유 테이블이다. 전부 COUNT(*) 집계 조회만 하며, 절대 쓰지 않는다
 * (PointService.countActiveDisputes와 같은 "읽기 전용 조회" 선례를 따름).
 */
@Mapper
public interface AdminNotificationMapper {

    /** 오늘 신규가입 회원 수 (USERS, 담당자1 소유 — 읽기 전용) */
    int countNewSignupsToday();

    /** 오늘 탈퇴 처리된 회원 수 (USERS, 담당자1 소유 — 읽기 전용) */
    int countWithdrawalsToday();

    /** 심사 대기 중인 제공자 신청 수 (PROVIDER_APPLY, 담당자7 소유 — 읽기 전용) */
    int countPendingProviderApply();

    /** 접수 대기 중인 신고 수 (ABUSE_REPORT, 담당자7 소유 — 읽기 전용) */
    int countPendingReports();

    /** N시간 이내 종료되는 진행 중 경매 수 (AUCTION, 담당자5 소유 — 읽기 전용) */
    int countAuctionsEndingSoon(@Param("hours") int hours);

    /** 오늘 등록된 서비스 요청 수 (SERVICE_REQUEST, 담당자7 소유 — 읽기 전용) */
    int countNewServiceRequestsToday();
}
