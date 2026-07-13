package nct.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * [로그인 요청]
 */
@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;

    /** 로그인 유지 여부 (true: Refresh 쿠키 14일, false: 세션 쿠키) */
    private boolean rememberMe;
}
