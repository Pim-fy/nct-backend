package nct.auth.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

// @ai_generated
/** 인증번호 원문 없이 가입 화면이 다음 동작을 제어할 수 있는 발송 결과다. */
@Getter
@Builder
public class EmailVerificationSendResponse {

    private final Long verificationId;
    private final LocalDateTime expiresAt;
    private final LocalDateTime resendAvailableAt;
}
