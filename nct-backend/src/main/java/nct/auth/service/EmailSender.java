package nct.auth.service;

// @ai_generated
/** 실제 메일 공급자와 가입 인증 상태 로직을 분리하는 발송 포트다. */
public interface EmailSender {

    void sendVerificationCode(String email, String code);

    // @ai_generated: F-AUTH-007 - 비밀번호 재설정은 코드가 아닌 링크(URL)를 발송한다.
    void sendPasswordResetLink(String email, String link);

    // @ai_generated: F-AUTH-011 - 정지 계정용 탈퇴 확인도 링크(URL) 방식이다.
    void sendWithdrawalLink(String email, String link);

    // @ai_generated: F-AUTH-017/POL-AUTH-016 - 정지 계정 비로그인 문의(INQC0010)에 관리자가
    // 답변하면 등록된 이메일로 원문 질문+답변을 통보한다. 일방향(회신 불가) 안내를 포함한다.
    void sendSuspendedInquiryAnswer(String email, String question, String answer);
}
