package nct.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import nct.global.validation.PasswordPolicy;
import lombok.Getter;
import lombok.Setter;

// @ai_generated
/** F-AUTH-007: 이메일 링크의 토큰과 새 비밀번호를 함께 제출하는 확정 요청이다. */
@Getter
@Setter
public class PasswordResetConfirmRequest {

    @NotBlank(message = "토큰은 필수입니다.")
    private String token;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Pattern(regexp = PasswordPolicy.REGEXP, message = PasswordPolicy.MESSAGE)
    private String newPassword;
}
