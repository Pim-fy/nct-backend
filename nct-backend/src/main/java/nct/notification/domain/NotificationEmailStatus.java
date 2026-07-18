package nct.notification.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [알림 - 이메일 발송 상태 코드] (F-COM-006)
 * - NOTIFICATION.NTF_EMAIL_STATUS_CD에 저장되는 공통코드(NTFG02)
 * - 알림 한 건마다 "이메일 보조 발송을 했는지, 결과가 어땠는지"를 함께 기록한다
 *   (정본 규칙: 중요 이벤트를 이메일 발송 "후보로 관리")
 */
@Getter
@RequiredArgsConstructor
public enum NotificationEmailStatus {

    /** 이메일 발송 대상 아님 — 일반 알림, 수신 거부, 전역 스위치 꺼짐, 메일 미설정 환경 */
    NONE("NTFC0006"),
    /** 발송 대기 (발송 시도 직전의 중간 상태) */
    PENDING("NTFC0007"),
    /** 발송 성공 */
    SENT("NTFC0008"),
    /** 발송 실패 — 인앱 알림은 정상 생성되고 이메일만 실패한 상태 (베스트 에포트) */
    FAILED("NTFC0009");

    /** DB에 저장되는 공통코드 값 */
    private final String code;
}
