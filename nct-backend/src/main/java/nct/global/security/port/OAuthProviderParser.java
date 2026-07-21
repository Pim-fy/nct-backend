package nct.global.security.port;

import java.util.Map;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import nct.global.security.handler.OAuth2ErrorCode;

// @ai_generated: 작업단위5 작업 2 - 로그인(CustomOAuth2UserService)·연동(OAuthLinkUserService) 양쪽이
// 공유하는 provider 응답 파싱 유틸. 작업 1에서 검증(Evaluator P3 반영)까지 마친 로직을 위치만 옮겼다 -
// 파싱 동작 자체는 바꾸지 않았다.
/** OAuth2 provider(카카오·네이버·구글) 응답 파싱 + provider 식별자 변환 */
public final class OAuthProviderParser {

    private OAuthProviderParser() {
    }

    // docs/260716_08_DB_기초데이터_v3.sql 기준(USRG02 하위코드): USRC0004=카카오, USRC0005=네이버, USRC0006=구글
    private static final String KAKAO_CD  = "USRC0004";
    private static final String NAVER_CD  = "USRC0005";
    private static final String GOOGLE_CD = "USRC0006";
    private static final String LINK_SUFFIX = "-link";

    public record ParsedOAuthProfile(String providerCd, String providerKey, String email,
                                     String nickname, String nameAttributeKey) {
    }

    /**
     * registrationId(로그인용 "kakao" 또는 연동용 "kakao-link")별 응답을 일반화 파싱한다.
     * loadUser 안(Spring Security 필터 체인)에서만 호출되므로 실패 시 OAuth2AuthenticationException을 던진다.
     */
    public static ParsedOAuthProfile parse(String registrationId, Map<String, Object> attributes) {
        return switch (stripLinkSuffix(registrationId)) {
            case "kakao"  -> parseKakao(attributes);
            case "naver"  -> parseNaver(attributes);
            case "google" -> parseGoogle(attributes);
            default -> throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCode.OAUTH_UNSUPPORTED_PROVIDER));
        };
    }

    /** REST API 응답용 - USER_OAUTH.USR_OAT_PROVIDER_CD(CMM_CD) -> 프론트 친화적 키("kakao" 등) */
    public static String providerCdToFriendlyKey(String providerCd) {
        return switch (providerCd) {
            case KAKAO_CD  -> "kakao";
            case NAVER_CD  -> "naver";
            case GOOGLE_CD -> "google";
            default -> throw new IllegalArgumentException("알 수 없는 OAuth provider 코드: " + providerCd);
        };
    }

    /** REST API 요청용 - 프론트가 보낸 친화적 키("kakao" 등) -> USR_OAT_PROVIDER_CD(CMM_CD) */
    public static String friendlyKeyToProviderCd(String friendlyKey) {
        return switch (friendlyKey) {
            case "kakao"  -> KAKAO_CD;
            case "naver"  -> NAVER_CD;
            case "google" -> GOOGLE_CD;
            default -> throw new IllegalArgumentException("알 수 없는 OAuth provider: " + friendlyKey);
        };
    }

    private static String stripLinkSuffix(String registrationId) {
        return registrationId.endsWith(LINK_SUFFIX)
            ? registrationId.substring(0, registrationId.length() - LINK_SUFFIX.length())
            : registrationId;
    }

    // 카카오 응답 구조: { id, kakao_account: { email, profile: { nickname } } }
    // @ai_generated: Evaluator 지적(P3) 반영 - 선택 동의 거부 시 kakao_account/profile 자체가
    // 응답에서 빠질 수 있어 무방어 캐스팅은 NPE로 이어진다. asMap()으로 안전하게 처리한다.
    private static ParsedOAuthProfile parseKakao(Map<String, Object> attributes) {
        Map<String, Object> kakaoAccount = asMap(attributes.get("kakao_account"));
        Map<String, Object> profile = asMap(kakaoAccount != null ? kakaoAccount.get("profile") : null);
        return new ParsedOAuthProfile(
            KAKAO_CD,
            String.valueOf(attributes.get("id")),
            kakaoAccount != null ? (String) kakaoAccount.get("email") : null,
            profile != null ? (String) profile.get("nickname") : null,
            "id");
    }

    // 네이버 응답 구조: { resultcode, message, response: { id, email, name } }
    private static ParsedOAuthProfile parseNaver(Map<String, Object> attributes) {
        Map<String, Object> response = asMap(attributes.get("response"));
        return new ParsedOAuthProfile(
            NAVER_CD,
            response != null ? (String) response.get("id") : null,
            response != null ? (String) response.get("email") : null,
            response != null ? (String) response.get("name") : null,
            "response");
    }

    // 구글 응답 구조: { sub, email, name, picture, ... } - 중첩 없이 평평한 구조
    private static ParsedOAuthProfile parseGoogle(Map<String, Object> attributes) {
        return new ParsedOAuthProfile(
            GOOGLE_CD,
            (String) attributes.get("sub"),
            (String) attributes.get("email"),
            (String) attributes.get("name"),
            "sub");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (value instanceof Map) ? (Map<String, Object>) value : null;
    }
}
