package nct.auth.dto;

import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.Setter;

/**
 * [로그인 요청]
 */
@Getter
@Setter
public class LoginRequest {

    // @ai_generated: 기존 프론트의 email payload는 전환 기간에만 loginId 별칭으로 수용한다.
    @NotBlank(message = "로그인 아이디는 필수입니다.")
    @JsonAlias("email")
    private String loginId;

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;

    /** 로그인 유지 여부 (true: Refresh 쿠키 14일, false: 세션 쿠키) */
    private boolean rememberMe;
}
