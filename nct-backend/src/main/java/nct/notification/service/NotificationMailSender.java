package nct.notification.service;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [알림 - 이메일 발송기 계약] (F-COM-006)
 * - 인증번호 발송기(nct.auth.EmailSender — 담당자1 소유)와 별개로 알림 도메인 전용 발송기를 둔다:
 *   용도가 다르고(인증 필수 발송 vs 알림 베스트 에포트), 타 담당자 인터페이스를 확장하지 않기 위함
 * - 구현체는 절대 예외를 던지지 않는다 — 이메일은 보조 채널이라 실패해도 본 처리(인앱 알림)에
 *   영향을 주면 안 된다 (베스트 에포트 원칙)
 */
public interface NotificationMailSender {

    /** 이 환경에서 메일 발송이 가능한가 (git 미추적 메일 설정이 없는 팀원 PC에서는 false) */
    boolean isAvailable();

    /**
     * 알림 이메일 발송 시도
     * @return 발송 성공 여부 — 실패해도 예외 없이 false만 돌려준다
     */
    boolean send(String toEmail, String subject, String body);
}
