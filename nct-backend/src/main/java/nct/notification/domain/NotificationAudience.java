package nct.notification.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Claude Code 작성 (BJN, 2026-07-17)
 *
 * [알림 - 알림 대상 구분 코드] (F-COM-011, 팀 결정 2026-07-17: ⓐ분류표+ⓑ구분 컬럼 방식)
 * - NOTIFICATION.NTF_AUDIENCE_CD (NTFG04)
 * - "내가 소비자로서 받은 알림(일반)"과 "내가 서비스 제공자로서 받은 업무 알림(제공자)"을
 *   구분한다 — 알림함의 일반/제공자 필터 기준
 * - 발행 시 분류 기준(ⓐ 분류표)은 팀전달_알림구분_260717.md 참조 — 새 알림을 발행하는
 *   담당자는 그 표에 따라 구분값을 정한다 (기본값은 일반)
 */
@Getter
@RequiredArgsConstructor
public enum NotificationAudience {

    /** 일반 활동 — 구매·입찰·충전·환전 등 소비자 관점 알림 */
    GENERAL("NTFC0015"),
    /** 제공자 업무 — 정산·견적 요청 등 판매자/서비스 제공자 관점 알림 */
    PROVIDER("NTFC0016");

    /** DB에 저장되는 공통코드 값 */
    private final String code;
}
