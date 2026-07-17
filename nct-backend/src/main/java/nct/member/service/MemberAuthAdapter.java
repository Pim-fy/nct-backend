package nct.member.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.global.security.port.LocalSignUpProfile;
import nct.global.security.port.OAuthProfile;
import nct.member.domain.Member;
import nct.member.mapper.MemberMapper;

import lombok.RequiredArgsConstructor;

/**
 * [AuthMemberPort 구현체 - 샘플]
 *
 * security/auth 모듈과 이 프로젝트의 회원 테이블(members)을 연결하는 어댑터.
 * 다른 프로젝트에 이식할 때는 이 클래스만 자신의 회원 도메인에 맞게 다시 작성하면 된다.
 *
 * 역할
 *   1) Member(도메인) <-> AuthMember(보안 모듈 모델) 변환
 *   2) 가입/토큰 갱신 등 인증 관련 DB 작업 위임
 */
@Service
@RequiredArgsConstructor
public class MemberAuthAdapter implements AuthMemberPort {

    private final MemberMapper memberMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthMember> findByEmail(String email) {
        return memberMapper.findMemberByEmail(email)
                           .map(this::toAuthMember);
    }

    // @ai_generated: 로컬 로그인 전용 조회. JWT 재발급/OAuth의 이메일 조회와 분리한다.
    @Override
    @Transactional(readOnly = true)
    public Optional<AuthMember> findByLoginId(String loginId) {
        return memberMapper.findMemberByLoginId(loginId)
                           .map(this::toAuthMember);
    }

    // @ai_generated: 가입 최종 단계의 사전 중복 확인을 MemberMapper에 위임한다.
    @Override
    @Transactional(readOnly = true)
    public boolean existsByLoginId(String loginId) {
        return memberMapper.existsByLoginId(loginId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByNickname(String nickname) {
        return memberMapper.existsByNickname(nickname);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return memberMapper.existsByEmail(email);
    }

    @Override
    @Transactional
    public AuthMember registerLocalMember(LocalSignUpProfile profile) {
        Member member = Member.builder()
            // @ai_generated: 로그인 ID와 이메일은 정본에 따라 서로 다른 컬럼으로 저장한다.
            .usrLoginId(profile.getLoginId())
            .usrPswdHash(profile.getEncodedPassword())
            .usrNm(profile.getNickname())
            .usrEml(profile.getEmail())
            .usrEmlCertYn('Y')
            .usrTelno(profile.getTelno())
            .usrStatusCd("USRC0001")          // 신규가입 기본 상태 (seed 기준: 활성/기본 정상 회원)
            .usrRoleCd("ROLE_USER")           // 신규가입 기본 역할 (DB DEFAULT 와 동일값 - 반환 객체에도 채워 응답 role 누락 방지)
            .build();
        memberMapper.saveCertifiedMember(member);   // useGeneratedKeys 로 usrSn 채워짐
        return toAuthMember(member);
    }

    @Override
    @Transactional
    public AuthMember registerOAuthMember(OAuthProfile profile) {
        // TODO: 현재 스키마로는 OAuth 가입을 완전히 구현할 수 없음
        //   1) USERS.USR_PSWD_HASH가 NOT NULL이라 비밀번호 없는 소셜 가입 저장 불가
        //   2) 소셜 로그인 연동 정보(USR_OAT_PROVIDER_CD 등)는 별도 USER_OAUTH 테이블인데 매퍼 미구현
        // 임의 비밀번호를 지어내는 건 보안상 위험해서(전 계정 공용 비밀번호가 될 수 있음) 시도하지 않음.
        throw new UnsupportedOperationException(
            "OAuth 회원가입 미구현: USERS.USR_PSWD_HASH NOT NULL 제약 및 USER_OAUTH 연동 매퍼 필요");
    }

    @Override
    @Transactional
    public void updateRefreshToken(Long usrSn, String refreshToken) {
        // JwtProvider 발급 토큰 원문을 그대로 저장(로그아웃 시 null 전달 -> null 저장).
        // 검증(AuthService.verifyRefreshToken)에서 원문 대조 + JwtProvider 로 서명/만료 확인.
        memberMapper.updateRefreshTokenById(usrSn, refreshToken);
    }

    /** Member(도메인) -> AuthMember(보안 모듈 전용 모델) 변환 */
    private AuthMember toAuthMember(Member member) {
        // @ai_generated: USERS.USR_NM은 정본의 닉네임이며 AuthMember의 name/nickname 응답에 같은 값을 준다.
        return AuthMember.builder()
                         .id(member.getUsrSn())
                         .email(member.getUsrEml())
                         .password(member.getUsrPswdHash())
                         .name(member.getUsrNm())
                         .nickname(member.getUsrNm())
                         .role(member.getUsrRoleCd())
                         .refreshToken(member.getUsrRefreshTokenHash())  // DB 저장 원문(재발급 검증용)
                         .build();
    }
}
