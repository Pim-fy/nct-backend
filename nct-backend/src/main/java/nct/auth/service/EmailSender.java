package nct.auth.service;

// @ai_generated
/** 실제 메일 공급자와 가입 인증 상태 로직을 분리하는 발송 포트다. */
public interface EmailSender {

    void sendVerificationCode(String email, String code);
}
