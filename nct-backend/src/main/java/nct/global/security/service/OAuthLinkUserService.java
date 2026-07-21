package nct.global.security.service;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import nct.auth.domain.UserOauthLinkRow;
import nct.auth.mapper.UserOauthMapper;
import nct.global.exception.CustomException;
import nct.global.security.domain.CustomUserDetails;
import nct.global.security.handler.OAuth2ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.global.security.port.OAuthProviderParser;
import nct.global.security.port.OAuthProviderParser.ParsedOAuthProfile;
import nct.global.security.provider.JwtTokenProvider;
import nct.global.utils.CookieUtil;

/**
 * [OAuth2 계정 연동(link) 전용 사용자 조회]
 * - 작업단위5 작업 2 (F-AUTH-016) - SPEC 설계 결정(F): 로그인용 CustomOAuth2UserService와 완전히
 *   분리된 별도 필터 체인(oauthLinkFilterChain)에서만 사용된다.
 * - 요청의 JWT 쿠키로 "누구에게 연동할지"를 식별한다(필수 로직 - 없으면 대상 사용자를 알 수 없음).
 *   쿠키가 없거나 만료됐으면 로그인/가입으로 폴백하지 않고 명확한 에러로 리다이렉트한다.
 * - 연동 성공해도 로그인 사용자를 바꾸거나 토큰을 재발급하지 않는다(OAuth2LinkSuccessHandler가 처리).
 */
@Service
@RequiredArgsConstructor
public class OAuthLinkUserService extends DefaultOAuth2UserService {

    private final CookieUtil cookieUtil;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthMemberPort authMemberPort;
    private final UserOauthMapper userOauthMapper;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        Long currentUsrSn = requireCurrentUser();
        ParsedOAuthProfile parsed = OAuthProviderParser.parse(registrationId, attributes);

        List<UserOauthLinkRow> existingLinks = userOauthMapper.findByUsrSn(currentUsrSn);
        // @ai_generated: F-AUTH-016 - 동일 제공자 중복 연동 제한(UK_USER_OAUTH_USR_PROVIDER)을
        // DB 제약에만 맡기지 않고 사전에 명확한 에러로 안내한다.
        boolean alreadyLinkedSameProvider = existingLinks.stream()
                .anyMatch(link -> link.providerCd().equals(parsed.providerCd()));
        if (alreadyLinkedSameProvider) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCode.ALREADY_LINKED_SELF));
        }

        authMemberPort.findByOauthProviderKey(parsed.providerCd(), parsed.providerKey())
                .ifPresent(other -> {
                    throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCode.ALREADY_LINKED_ELSEWHERE));
                });

        // @ai_generated: Evaluator 지적(P3) 반영 - 위 findByUsrSn/findByOauthProviderKey 사전 확인과
        // 이 INSERT 사이의 TOCTOU 경쟁(더블클릭 등)으로 DB 제약(UK_USER_OAUTH_USR_PROVIDER/
        // UK_USER_OAUTH_PROVIDER_KEY)이 위반되면 DataIntegrityViolationException이 그대로 터진다.
        // OAuth2AuthenticationException이 아니라서 OAuth2LinkFailureHandler를 우회해 500으로
        // 전파되므로(작업 1의 CustomOAuth2UserService.registerNewOAuthMember와 동일 패턴으로) 잡아
        // 변환한다.
        try {
            userOauthMapper.insert(currentUsrSn, parsed.providerCd(), parsed.providerKey());
        } catch (DataIntegrityViolationException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCode.ALREADY_LINKED_SELF));
        }

        AuthMember currentMember = authMemberPort.findById(currentUsrSn)
                .orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCode.LINK_AUTH_REQUIRED)));

        return new CustomUserDetails(currentMember, attributes, parsed.nameAttributeKey());
    }

    /** 요청의 access_token 쿠키로 현재 로그인 사용자를 식별한다. 없거나 만료됐으면 폴백 없이 실패시킨다. */
    private Long requireCurrentUser() {
        HttpServletRequest request = currentHttpRequest();
        String accessToken = cookieUtil.extractCookie(request, CookieUtil.ACCESS_TOKEN_COOKIE);
        if (accessToken == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCode.LINK_AUTH_REQUIRED));
        }
        try {
            return jwtTokenProvider.getUsrSn(accessToken);
        } catch (CustomException ex) {
            // @ai_generated: 만료/위조 토큰 - CustomException은 이 필터 체인에서 처리되지 않으므로
            // OAuth2AuthenticationException으로 변환해야 OAuth2LinkFailureHandler가 받는다.
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCode.LINK_AUTH_REQUIRED));
        }
    }

    private HttpServletRequest currentHttpRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }
}
