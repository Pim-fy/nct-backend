package nct.global.security.service;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import nct.global.security.domain.CustomUserDetails;
import nct.global.security.handler.OAuth2ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.global.security.port.OAuthProviderParser;
import nct.global.security.port.OAuthProviderParser.ParsedOAuthProfile;
import nct.global.security.provider.OAuthOnboardingTokenProvider;
import nct.global.utils.CookieUtil;

/**
 * [OAuth2(카카오·네이버·구글) 사용자 조회/로그인]
 * - registrationId(provider)별로 다른 응답 구조는 OAuthProviderParser가 일반화 파싱한다(작업 2부터
 *   연동 흐름과 공유하기 위해 분리 - 로직 자체는 바뀌지 않았다).
 * - USER_OAUTH(provider, providerKey) 기준으로 기존 연동 회원을 조회한다.
 * - ISS-001: 반환 직전 계정 상태(정지·탈퇴)를 검사해 로컬 로그인과 동일하게 차단한다.
 * - ISS-009/POL-AUTH-015: 미연동(최초) 사용자는 여기서 더 이상 즉시 가입시키지 않는다 - 파싱된
 *   정보를 온보딩 토큰에 담아 쿠키로 내려주고 온보딩 화면(약관 동의+닉네임 확정)으로 보낸다.
 *   실제 계정 생성은 `OauthOnboardingService`(온보딩 제출 시점)가 담당한다.
 *
 * 이 메서드는 컨트롤러가 아니라 Spring Security 필터 체인 안에서 호출되므로, 실패는 반드시
 * OAuth2AuthenticationException으로 던져야 OAuth2FailureHandler가 받아 프론트로 안전하게 리다이렉트한다
 * (CustomException을 던지면 @ControllerAdvice가 이 계층에 개입하지 못해 500으로 떨어진다).
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    // @ai_generated: USRG01(회원 상태) 코드값 - AuthService/CustomUserDetailsService와 동일 기준
    private static final String STATUS_SUSPENDED = "USRC0002";
    private static final String STATUS_WITHDRAWN = "USRC0003";

    private final AuthMemberPort authMemberPort;
    private final OAuthOnboardingTokenProvider onboardingTokenProvider;
    private final CookieUtil cookieUtil;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        ParsedOAuthProfile parsed = OAuthProviderParser.parse(registrationId, attributes);

        if (parsed.email() == null || parsed.email().isBlank()) {
            // @ai_generated: SPEC 설계 결정(C) - 이메일 미동의 시 가짜 이메일을 지어내지 않고 가입 자체를 거부한다.
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCode.OAUTH_EMAIL_REQUIRED));
        }

        Optional<AuthMember> existingMember =
                authMemberPort.findByOauthProviderKey(parsed.providerCd(), parsed.providerKey());
        if (existingMember.isEmpty()) {
            // @ai_generated: ISS-009/POL-AUTH-015 - 즉시 가입 대신 온보딩으로 분기(항상 예외를 던진다).
            triggerOnboarding(parsed);
        }

        AuthMember member = existingMember.get();
        requireActiveStatus(member.getStatus());

        return new CustomUserDetails(member, attributes, parsed.nameAttributeKey());
    }

    /** 파싱된 정보를 온보딩 토큰(쿠키)에 담아 내려주고, 로그인 실패 경로로 온보딩 화면까지 리다이렉트시킨다. */
    private void triggerOnboarding(ParsedOAuthProfile parsed) {
        String onboardingToken = onboardingTokenProvider.createToken(
                parsed.providerCd(), parsed.providerKey(), parsed.email(), parsed.nickname());
        currentHttpResponse().addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.createOnboardingTokenCookie(onboardingToken).toString());
        throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCode.ONBOARDING_REQUIRED));
    }

    /** F-AUTH-009/ISS-001: 계정 상태가 활성이 아니면 OAuth 로그인도 차단한다(로컬 로그인과 동일 기준). */
    private void requireActiveStatus(String status) {
        if (STATUS_SUSPENDED.equals(status)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCode.ACCOUNT_SUSPENDED));
        }
        if (STATUS_WITHDRAWN.equals(status)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCode.WITHDRAWN_USER));
        }
    }

    // @ai_generated: 작업2의 OAuthLinkUserService와 동일 기법 - loadUser는 컨트롤러가 아니라
    // 필터 체인 콜백이라 HttpServletResponse를 직접 못 받는다.
    private HttpServletResponse currentHttpResponse() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getResponse();
    }
}
