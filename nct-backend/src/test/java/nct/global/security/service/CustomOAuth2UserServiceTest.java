package nct.global.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import nct.global.security.handler.OAuth2ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.global.security.provider.OAuthOnboardingTokenProvider;
import nct.global.utils.CookieUtil;

class CustomOAuth2UserServiceTest {

    private final CustomOAuth2UserService service = new CustomOAuth2UserService(
            mock(AuthMemberPort.class),
            mock(OAuthOnboardingTokenProvider.class),
            mock(CookieUtil.class));

    // 담당자 7 · F-OPS-001: 관리자 계정은 사용자용 OAuth 흐름에서 일반 실패로 차단한다.
    @Test
    void 관리자_계정은_OAuth_로그인을_사용할_수_없다() {
        AuthMember admin = AuthMember.builder().id(101L).role("ROLE_ADMIN").build();

        assertThatThrownBy(() -> service.requireOAuthLoginAllowed(admin))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        exception -> assertThat(exception.getError().getErrorCode())
                                .isEqualTo(OAuth2ErrorCode.OAUTH_LOGIN_FAILED));
    }

    @Test
    void 일반회원과_제공자는_OAuth_로그인을_계속_사용할_수_있다() {
        AuthMember user = AuthMember.builder().id(101L).role("ROLE_USER").build();
        AuthMember provider = AuthMember.builder().id(102L).role("ROLE_SERVICE").build();

        assertThatCode(() -> service.requireOAuthLoginAllowed(user)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireOAuthLoginAllowed(provider)).doesNotThrowAnyException();
    }
}
