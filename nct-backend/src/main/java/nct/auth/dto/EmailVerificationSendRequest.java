package nct.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// @ai_generated
/** SIGNUP 인증번호 발송 전 필수 약관 동의 여부를 함께 검증하는 요청이다. */
@Getter
@Setter
public class EmailVerificationSendRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    @NotNull(message = "서비스 이용약관 동의 여부는 필수입니다.")
    private Boolean termsAgreed;

    @NotNull(message = "개인정보 처리방침 동의 여부는 필수입니다.")
    private Boolean privacyAgreed;
}
