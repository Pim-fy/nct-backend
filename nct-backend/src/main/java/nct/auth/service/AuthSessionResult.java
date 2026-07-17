package nct.auth.service;

import nct.auth.dto.LoginResponse;
import lombok.Builder;
import lombok.Getter;

// @ai_generated
/** Service가 HTTP 쿠키 대신 로그인 결과와 발급 토큰을 Controller에 전달하는 내부 모델이다. */
@Getter
@Builder
public class AuthSessionResult {

    private final LoginResponse loginResponse;
    private final String accessToken;
    private final String refreshToken;
}
