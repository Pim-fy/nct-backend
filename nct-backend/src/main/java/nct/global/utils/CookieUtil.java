package nct.global.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * [JWT httpOnly 쿠키 유틸]
 * - httpOnly : JavaScript 접근 불가 -> XSS 로 토큰 탈취 방지
 * - secure   : HTTPS 에서만 전송 (운영 배포 시 cookie.secure=true)
 * - SameSite=Lax : CSRF 완화
 * - Refresh 쿠키는 path=/api/auth 로 제한 -> 재발급/로그아웃 요청에만 전송
 */
@Component
public class CookieUtil {

    public static final String ACCESS_TOKEN_COOKIE  = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    // @ai_generated: 작업단위5(F-AUTH-004 온보딩) - 소셜 최초 가입 온보딩 임시 토큰 전용 쿠키
    public static final String ONBOARDING_TOKEN_COOKIE = "oauth_onboarding_token";

    /** Refresh 쿠키를 전송할 경로 (재발급·로그아웃 API 프리픽스) */
    private static final String REFRESH_TOKEN_PATH = "/api/auth";
    /** 온보딩 쿠키를 전송할 경로 (온보딩 완료 API 프리픽스) */
    private static final String ONBOARDING_TOKEN_PATH = "/api/auth/oauth-onboarding";
    // @ai_generated: 온보딩 쿠키 수명(초) - OAuthOnboardingTokenProvider의 토큰 만료(10분)와 동일
    private static final int ONBOARDING_TOKEN_MAX_AGE = 600;

    @Value("${cookie.secure:false}")
    private boolean secure;

    /** Access Token 쿠키 수명 (초) */
    @Value("${cookie.access-token-max-age:1800}")
    private int accessTokenMaxAge;

    /** 로그인 유지 시 Refresh Token 쿠키 수명 (초) */
    @Value("${cookie.refresh-token-max-age:1209600}")
    private long refreshTokenMaxAge;

    /** Access Token httpOnly 쿠키 생성 */
    public ResponseCookie createAccessTokenCookie(String accessToken) {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE, accessToken)
                             .httpOnly(true)
                             .secure(secure)
                             .sameSite("Lax")
                             .path("/")
                             .maxAge(accessTokenMaxAge)
                             .build();
    }

    /**
     * Refresh Token httpOnly 쿠키 생성
     * @param rememberMe true  -> 설정된 수명(기본 14일)의 영속 쿠키
     *                   false -> 세션 쿠키 (브라우저 종료 시 소멸)
     */
    public ResponseCookie createRefreshTokenCookie(String refreshToken, boolean rememberMe) {
        long maxAge = rememberMe ? refreshTokenMaxAge : -1;
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                             .httpOnly(true)
                             .secure(secure)
                             .sameSite("Lax")
                             .path(REFRESH_TOKEN_PATH)
                             .maxAge(maxAge)
                             .build();
    }

    /** Access Token 쿠키 삭제 (Max-Age=0) */
    public ResponseCookie deleteAccessTokenCookie() {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE, "")
                             .httpOnly(true)
                             .secure(secure)
                             .sameSite("Lax")
                             .path("/")
                             .maxAge(0)
                             .build();
    }

    /** Refresh Token 쿠키 삭제 (Max-Age=0) */
    public ResponseCookie deleteRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                             .httpOnly(true)
                             .secure(secure)
                             .sameSite("Lax")
                             .path(REFRESH_TOKEN_PATH)
                             .maxAge(0)
                             .build();
    }

    /** 온보딩 토큰 httpOnly 쿠키 생성 */
    public ResponseCookie createOnboardingTokenCookie(String onboardingToken) {
        return ResponseCookie.from(ONBOARDING_TOKEN_COOKIE, onboardingToken)
                             .httpOnly(true)
                             .secure(secure)
                             .sameSite("Lax")
                             .path(ONBOARDING_TOKEN_PATH)
                             .maxAge(ONBOARDING_TOKEN_MAX_AGE)
                             .build();
    }

    /** 온보딩 토큰 쿠키 삭제 (Max-Age=0) */
    public ResponseCookie deleteOnboardingTokenCookie() {
        return ResponseCookie.from(ONBOARDING_TOKEN_COOKIE, "")
                             .httpOnly(true)
                             .secure(secure)
                             .sameSite("Lax")
                             .path(ONBOARDING_TOKEN_PATH)
                             .maxAge(0)
                             .build();
    }

    /** 요청 쿠키에서 특정 쿠키 값 추출 (없으면 null) */
    public String extractCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
