package nct.global.security.handler;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// @ai_generated: 작업단위5 작업 2 - SPEC 설계 결정(F) - 로그인용 OAuth2SuccessHandler와 달리 토큰을
// 재발급하지 않는다(연동은 이미 로그인된 사용자의 부가 동작이라 다른 기기 로그아웃을 유발하면 안 됨).
/** [OAuth2 계정 연동 성공 처리] - 토큰 재발급 없이 프론트 마이페이지로 리다이렉트 */
@Component
public class OAuth2LinkSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final String LINK_SUFFIX = "-link";

    // @ai_generated: application.properties는 AI 접근이 차단돼 값을 직접 넣을 수 없다. 기본값을
    // 로그인용 redirect-url과 동일하게 둬서(담당자3이 별도 마이페이지 완료 페이지를 아직 안 정했을
    // 수 있음) 프로퍼티 미설정 시에도 스프링 컨텍스트 기동이 깨지지 않게 한다. 전용 URL이 필요하면
    // application.properties에 app.oauth2.link-redirect-url을 별도로 추가하면 된다.
    /** 연동 완료 후 프론트가 결과를 보여줄 처리 페이지 (application.properties, 미설정 시 로그인용과 동일) */
    @Value("${app.oauth2.link-redirect-url:${app.oauth2.redirect-url}}")
    private String linkRedirectUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String provider = friendlyProvider(authentication);

        String target = UriComponentsBuilder.fromUriString(linkRedirectUrl)
                                            .queryParam("linkSuccess", "true")
                                            .queryParam("provider", provider)
                                            .build()
                                            .toUriString();

        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private String friendlyProvider(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken token) {
            String registrationId = token.getAuthorizedClientRegistrationId();
            return registrationId.endsWith(LINK_SUFFIX)
                ? registrationId.substring(0, registrationId.length() - LINK_SUFFIX.length())
                : registrationId;
        }
        return "unknown";
    }
}
