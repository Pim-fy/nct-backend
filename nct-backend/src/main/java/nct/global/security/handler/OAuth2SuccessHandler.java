package nct.global.security.handler;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import nct.global.security.domain.CustomUserDetails;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.global.security.provider.JwtTokenProvider;
import nct.global.utils.CookieUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * [OAuth2 로그인 성공 처리]
 * - 카카오 인증 성공 -> JWT 발급 -> httpOnly 쿠키 탑재 -> 프론트로 리다이렉트
 * - 소셜 로그인은 별도 체크박스가 없으므로 로그인 유지(rememberMe)로 처리
 */
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final CookieUtil cookieUtil;
    private final AuthMemberPort authMemberPort;

    /** 카카오 로그인 성공 후 프론트엔드 처리 페이지 (application.properties) */
    @Value("${app.oauth2.redirect-url}")
    private String redirectUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        AuthMember member = userDetails.getMember();

        // JWT 발급
        // @ai_generated: subject를 email(가변)에서 usrSn(불변 PK)으로 전환 - JwtTokenProvider 시그니처 변경에 따른 연쇄 수정
        String accessToken  = jwtTokenProvider.createAccessToken(member.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getId());

        // Refresh Token DB 저장
        authMemberPort.updateRefreshToken(member.getId(), refreshToken);

        // httpOnly 쿠키 탑재 (소셜 로그인은 rememberMe 고정)
        boolean rememberMe = true;
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.createAccessTokenCookie(accessToken).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.createRefreshTokenCookie(refreshToken, rememberMe).toString());

        // 프론트엔드 OAuth2 처리용 중간 페이지로 리다이렉트
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
