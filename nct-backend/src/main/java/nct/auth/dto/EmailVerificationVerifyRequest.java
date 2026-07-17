package nct.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

// @ai_generated
/** 가입 인증번호 검증 요청이다. 인증번호는 검증 뒤 저장·응답하지 않는다. */
@Getter
@Setter
public class EmailVerificationVerifyRequest {

    @NotBlank(message = "인증번호는 필수입니다.")
    @Pattern(regexp = "^\\d{6}$", message = "인증번호는 6자리 숫자여야 합니다.")
    private String code;
}
