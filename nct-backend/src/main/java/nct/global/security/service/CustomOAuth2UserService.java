package nct.global.security.service;

import java.util.Map;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import nct.global.security.domain.CustomUserDetails;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.global.security.port.OAuthProfile;

import lombok.RequiredArgsConstructor;

/**
 * [OAuth2(카카오) 사용자 조회/가입]
 * - 카카오 응답 JSON 은 kakao_account > profile 로 중첩되어 있어 꺼내서 사용
 * - 이메일로 기존 회원 조회, 없으면 포트를 통해 자동 가입 (최초 1회)
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final AuthMemberPort authMemberPort;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // 카카오 응답 구조: { id, kakao_account: { email, profile: { nickname } } }
        Map<String, Object> attributes = oAuth2User.getAttributes();
        @SuppressWarnings("unchecked")
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

        String email    = (String) kakaoAccount.get("email");
        String nickname = (String) profile.get("nickname");

        // 기존 회원 조회, 없으면 자동 가입 (소셜 로그인 최초 1회)
        AuthMember member = authMemberPort.findByEmail(email)
                                          .orElseGet(() -> authMemberPort.registerOAuthMember(
                                              OAuthProfile.builder()
                                                          .provider("KAKAO")
                                                          .email(email)
                                                          .nickname(nickname)
                                                          .build()));

        // 카카오 식별 속성명은 "id"
        return new CustomUserDetails(member, attributes, "id");
    }
}
