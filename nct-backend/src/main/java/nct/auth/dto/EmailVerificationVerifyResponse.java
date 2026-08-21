package nct.auth.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

// @ai_generated
/** 인증 성공 시 연장된 가입 유예 만료시각을 화면에 전달한다(발송 시점 3분과는 별개). */
@Getter
@Builder
public class EmailVerificationVerifyResponse {

    private final LocalDateTime expiresAt;
}
