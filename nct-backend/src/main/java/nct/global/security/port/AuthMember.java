package nct.global.security.port;

import lombok.Builder;
import lombok.Getter;

/**
 * [보안 모듈 전용 회원 모델]
 * - security/auth 모듈이 프로젝트별 회원 도메인(Member 등)을 직접 알지 못하도록
 *   인증에 필요한 최소 필드만 정의한 모델
 * - 새 프로젝트에서는 자신의 회원 도메인 -> AuthMember 변환만 구현하면 됨
 */
@Getter
@Builder
public class AuthMember {

    /** 회원 PK */
    private final Long id;

    /** 로그인 ID (이메일) */
    private final String email;

    /** BCrypt 암호화된 비밀번호 (소셜 가입은 null) */
    private final String password;

    /** 이름 */
    private final String name;

    /** 닉네임 */
    private final String nickname;

    /** 권한 (ROLE_USER / ROLE_ADMIN) */
    private final String role;

    /** 가입 경로 (LOCAL / KAKAO) */
    private final String provider;

    /** DB 에 저장된 Refresh Token (재발급 검증용) */
    private final String refreshToken;
}
