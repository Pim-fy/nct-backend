package nct.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// @ai_generated
/** F-AUTH-007: 비밀번호 재설정 링크 발송 요청이다. 계정 존재 여부와 무관하게 동일 응답을 반환한다. */
@Getter
@Setter
public class PasswordResetRequestDto {

    @NotBlank(message = "로그인 아이디는 필수입니다.")
    private String loginId;

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;
}
