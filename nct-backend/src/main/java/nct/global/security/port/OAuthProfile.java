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

    /** 가입 경로 (KAKAO 등) */
    private final String provider;

    /** 이메일 */
    private final String email;

    /** 닉네임 */
    private final String nickname;
}
