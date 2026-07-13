package nct.member.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.member.domain.Member;

@Mapper
public interface MemberMapper {

    Optional<Member> findMemberByEmail(String email);

    void saveMember(Member member);

    // #{refreshToken} 은 JwtProvider 발급 토큰 원문(로그아웃 시 null) - USR_REFRESH_TOKEN_HASH 컬럼에 저장
    void updateRefreshTokenById(@Param("usrSn") Long usrSn,
                                @Param("refreshToken") String refreshToken);
}
