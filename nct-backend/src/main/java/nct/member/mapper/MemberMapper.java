package nct.member.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.member.domain.Member;

@Mapper
public interface MemberMapper {

    Optional<Member> findMemberByEmail(String email);

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

    // @ai_generated: F-AUTH-007 - #{encodedPassword} 는 BCrypt 인코딩 완료 상태(PasswordResetService)
    void updatePasswordById(@Param("usrSn") Long usrSn,
                            @Param("encodedPassword") String encodedPassword);
}
