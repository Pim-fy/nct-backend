package nct.auth.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * [로그인/내정보 응답]
 * - 토큰은 응답 본문에 절대 포함하지 않음 (httpOnly 쿠키로만 전달)
 * - 프론트엔드 전역 상태(Context 등) 초기화에 필요한 정보만 반환
 */
@Getter
@Builder
public class LoginResponse {

    private final Long id;

    private final String email;

    private final String name;

    private final String nickname;

    private final String role;

    /** 가입 경로 (LOCAL / KAKAO) - 프론트에서 소셜 계정 안내 등에 사용 */
    private final String provider;
}
