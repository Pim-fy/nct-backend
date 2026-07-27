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

// @ai_generated: 작업단위5 작업 2 - OAuth2FailureHandler(로그인용)와 대칭이지만 리다이렉트 목적지가
// 다르다(마이페이지 처리 페이지). LINK_AUTH_REQUIRED·ALREADY_LINKED_ELSEWHERE·ALREADY_LINKED_SELF 등
// OAuthLinkUserService가 던진 사유를 그대로 프론트에 전달한다.
/** [OAuth2 계정 연동 실패 처리] */
@Component
public class OAuth2LinkFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final String LINK_SUFFIX = "-link";

    // @ai_generated: application.properties 접근 차단 - 기본값을 로그인용 redirect-url과 동일하게
    // 둬서 프로퍼티 미설정 시에도 컨텍스트 기동이 깨지지 않게 한다(OAuth2LinkSuccessHandler와 동일 이유).
    @Value("${app.oauth2.link-redirect-url:${app.oauth2.redirect-url}}")
    private String linkRedirectUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String errorCode = (exception instanceof OAuth2AuthenticationException oAuth2Exception)
            ? oAuth2Exception.getError().getErrorCode()
            : OAuth2ErrorCode.OAUTH_LOGIN_FAILED;

        String target = UriComponentsBuilder.fromUriString(linkRedirectUrl)
                                            .queryParam("linkError", errorCode)
                                            .queryParam("provider", friendlyProviderFromUri(request))
                                            .build()
                                            .toUriString();

        getRedirectStrategy().sendRedirect(request, response, target);
    }

    // @ai_generated: 콜백 URI 마지막 경로 조각이 registrationId다(/api/login/oauth2/code/{registrationId}).
    // 실패 시점엔 Authentication 객체가 없어 registrationId를 요청 URI에서 직접 뽑는다.
    private String friendlyProviderFromUri(HttpServletRequest request) {
        String path = request.getRequestURI();
        String registrationId = path.substring(path.lastIndexOf('/') + 1);
        return registrationId.endsWith(LINK_SUFFIX)
            ? registrationId.substring(0, registrationId.length() - LINK_SUFFIX.length())
            : registrationId;
    }
}
