package nct.auth.service;

// @ai_generated
/** 실제 메일 공급자와 가입 인증 상태 로직을 분리하는 발송 포트다. */
public interface EmailSender {

    void sendVerificationCode(String email, String code);

    // @ai_generated: F-AUTH-007 - 비밀번호 재설정은 코드가 아닌 링크(URL)를 발송한다.
    void sendPasswordResetLink(String email, String link);
}
