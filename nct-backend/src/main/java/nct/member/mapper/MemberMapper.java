package nct.member.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.member.domain.Member;

@Mapper
public interface MemberMapper {

    Optional<Member> findMemberByEmail(String email);

    // @ai_generated: F-AUTH-011 TOCTOU 하드닝 - 정지 계정 탈퇴 확정 전용 잠금 조회.
    // SELECT ... FOR UPDATE로 해당 회원 행을 트랜잭션 종료까지 잠가, 상태 재검사(확인)와
    // withdraw() 실행(사용) 사이에 다른 트랜잭션이 상태를 바꿀 수 없게 한다. 활성 탈퇴 경로
    // (MemberService.withdrawActive)와 공유 withdraw()에는 영향 없음 - 이 경로에서만 사용.
    Optional<Member> findMemberByEmailForUpdate(String email);

    // @ai_generated: 로컬 로그인과 가입 중복 확인용 USERS 조회다.
    Optional<Member> findMemberByLoginId(String loginId);

    // @ai_generated: JWT subject(usrSn) 기반 조회 - 로그인 필터·재발급이 가변 필드(email) 대신 사용
    Optional<Member> findMemberById(Long usrSn);

    boolean existsByLoginId(String loginId);

    boolean existsByNickname(String nickname);

    boolean existsByEmail(String email);

    void saveCertifiedMember(Member member);

    // @ai_generated: #{refreshTokenHash} 는 TokenHashUtil 로 해시화된 값(로그아웃 시 null) - USR_REFRESH_TOKEN_HASH 컬럼에 저장
    void updateRefreshTokenById(@Param("usrSn") Long usrSn,
                                @Param("refreshTokenHash") String refreshTokenHash);

    // @ai_generated: CHG-032/F-PROV-015 - 계정 자격이 아닌 현재 활성 접근 역할만 바꾼다.
    int updateRoleById(@Param("usrSn") Long usrSn,
                       @Param("role") String role);

    // @ai_generated: F-AUTH-007 - #{encodedPassword} 는 BCrypt 인코딩 완료 상태(PasswordResetService)
    void updatePasswordById(@Param("usrSn") Long usrSn,
                            @Param("encodedPassword") String encodedPassword);

    // @ai_generated: F-AUTH-010 - POL-AUTH-014로 확정된 4개 필드만 갱신한다.
    void updateProfile(@Param("usrSn") Long usrSn,
                       @Param("nickname") String nickname,
                       @Param("profileFileSn") Long profileFileSn,
                       @Param("email") String email,
                       @Param("bankName") String bankName,
                       @Param("accountNo") String accountNo);

    // @ai_generated: F-AUTH-011 - POL-AUTH-013 컬럼별 보존 범위를 한 트랜잭션으로 반영한다.
    void withdraw(@Param("usrSn") Long usrSn,
                 @Param("anonymizedEmail") String anonymizedEmail,
                 @Param("anonymizedNickname") String anonymizedNickname);
}
