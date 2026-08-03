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
    // @ai_generated: refresh 시 재발급된 refresh token 쿠키의 유지기간(30분/1일)을 컨트롤러가
    // 다시 판단할 필요 없도록 서비스가 함께 내려준다. 로그인 흐름은 요청 DTO에 이미 있어 안 씀.
    private final boolean rememberMe;
}
