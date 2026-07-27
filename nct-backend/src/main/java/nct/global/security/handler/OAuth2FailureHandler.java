package nct.global.security.handler;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// @ai_generated: 작업단위5 - OAuth2SuccessHandler와 대칭. failureHandler 미등록 시 Spring Security
// 기본값(백엔드 자체 "/login?error")으로 리다이렉트되어 프론트가 실패 사유를 받을 방법이 없었다
// (CustomOAuth2UserService가 던진 OAuth2AuthenticationException을 여기서 받아 프론트로 사유를 전달한다).
/**
 * [OAuth2 로그인 실패 처리]
 * - 계정 상태 차단(ISS-001)·이메일 미동의·이메일/닉네임 중복 등 실패 사유를 쿼리 파라미터로 실어
 *   프론트 OAuth2 처리 페이지로 리다이렉트한다.
 */
@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    /** 카카오·네이버·구글 공통 - 성공 시와 동일한 프론트 처리 페이지 (application.properties) */
    @Value("${app.oauth2.redirect-url}")
    private String redirectUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String errorCode = (exception instanceof OAuth2AuthenticationException oAuth2Exception)
            ? oAuth2Exception.getError().getErrorCode()
            : OAuth2ErrorCode.OAUTH_LOGIN_FAILED;

        // @ai_generated: 작업단위5(F-AUTH-004 온보딩, ISS-009) - 온보딩 필요는 "실패"가 아니라
        // 정상적인 다음 단계라 별도 쿼리 파라미터로 구분한다. 신규 프로퍼티(app.oauth2.onboarding-*)를
        // 추가하지 않고 기존 redirect-url을 그대로 재사용한다(이번 세션에 겪은 프로퍼티 기본값
        // 누락 사고를 반복하지 않기 위함).
        UriComponentsBuilder target = UriComponentsBuilder.fromUriString(redirectUrl);
        if (OAuth2ErrorCode.ONBOARDING_REQUIRED.equals(errorCode)) {
            target.queryParam("onboardingRequired", "true");
        } else {
            target.queryParam("oauthError", errorCode);
        }

        getRedirectStrategy().sendRedirect(request, response, target.build().toUriString());
    }
}
