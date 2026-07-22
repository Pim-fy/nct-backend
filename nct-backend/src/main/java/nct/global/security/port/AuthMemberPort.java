package nct.global.security.port;

import java.util.Optional;

/**
 * [재사용의 핵심 - 인증 회원 포트]
 *
 * security/auth 모듈과 프로젝트별 회원 도메인 사이의 유일한 연결 고리.
 * 새 프로젝트에 이식할 때는 이 인터페이스 구현체 하나만 작성하면
 * 로그인/JWT/카카오 로그인 전체가 그대로 동작한다.
 *
 * 샘플 구현: nct.member.service.MemberAuthAdapter
 */
public interface AuthMemberPort {

    /** 이메일로 인증용 회원 조회 (가입 중복 확인 등에 사용) */
    Optional<AuthMember> findByEmail(String email);

    // @ai_generated: 로컬 로그인과 가입 중복 확인은 이메일이 아닌 로그인 ID 기준으로 수행한다.
    Optional<AuthMember> findByLoginId(String loginId);

    // @ai_generated: JWT subject(usrSn) 기반 조회 - 필터 인가·토큰 재발급이 가변 필드(email) 대신 사용
    Optional<AuthMember> findById(Long usrSn);

    boolean existsByLoginId(String loginId);

    boolean existsByNickname(String nickname);

    boolean existsByEmail(String email);

    /** 일반 회원가입 (비밀번호는 인코딩 완료 상태로 전달됨) */
    AuthMember registerLocalMember(LocalSignUpProfile profile);

    /** 소셜 로그인 최초 1회 자동 가입 */
    AuthMember registerOAuthMember(OAuthProfile profile);

    // @ai_generated: 작업단위5 - OAuth 재로그인 시 기존 연동 회원 조회. email이 아니라
    // USER_OAUTH(provider, providerKey) 기준으로 찾아야 타 계정과의 의도치 않은 이메일 매칭을 막는다.
    /** USER_OAUTH 연동 기준 기존 회원 조회 */
    Optional<AuthMember> findByOauthProviderKey(String providerCd, String providerKey);

    /** Refresh Token 저장/삭제 (로그아웃 시 null 전달) */
    void updateRefreshToken(Long memberId, String refreshToken);

    /** @ai_generated CHG-032/F-PROV-015: 현재 활성 접근 역할을 DB에 반영한다. */
    int updateRole(Long memberId, String role);

    // @ai_generated: F-AUTH-007 - 비밀번호 재설정 완료 시 인코딩된 새 비밀번호로 교체
    /** 비밀번호 변경 (인코딩 완료 상태로 전달됨) */
    void updatePassword(Long memberId, String encodedPassword);
}
