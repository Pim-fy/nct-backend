package nct.global.security.port;

import lombok.Builder;
import lombok.Getter;

/**
 * [OAuth2 가입 프로필]
 * - 소셜 로그인 최초 1회 자동 가입 시 전달되는 정보
 */
@Getter
@Builder
public class OAuthProfile {

    // @ai_generated: 작업단위5 - "KAKAO" 같은 임의 문자열이 아니라 USER_OAUTH.USR_OAT_PROVIDER_CD에
    // 저장할 실제 CMM_CD 값(USRC0004/5/6)을 담는다. registrationId -> CMM_CD 매핑은 호출측(CustomOAuth2UserService)이 담당.
    /** 가입 경로 공통코드 (USRG02: USRC0004=카카오, USRC0005=네이버, USRC0006=구글) */
    private final String provider;

    /** 이메일 */
    private final String email;

    /** 닉네임 */
    private final String nickname;

    // @ai_generated: 작업단위5 - USER_OAUTH.USR_OAT_PROVIDER_KEY 저장·조회용 제공자 사용자 식별값
    /** 제공자가 부여한 사용자 식별값 (카카오: id, 네이버: response.id, 구글: sub) */
    private final String providerKey;
}
