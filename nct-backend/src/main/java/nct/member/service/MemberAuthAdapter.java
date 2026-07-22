package nct.member.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nct.auth.mapper.UserOauthMapper;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.global.security.port.LocalSignUpProfile;
import nct.global.security.port.OAuthProfile;
import nct.global.utils.TokenHashUtil;
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

    // @ai_generated: 작업단위5 - OAUTH_+ULID 로그인ID·CSPRNG 시스템 비밀번호 생성용
    private static final char[] CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int SYSTEM_PASSWORD_BYTES = 32;

    private final MemberMapper memberMapper;
    private final TokenHashUtil tokenHashUtil;
    private final PasswordEncoder passwordEncoder;
    private final UserOauthMapper userOauthMapper;
    private final SecureRandom secureRandom = new SecureRandom();

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

    // @ai_generated: JWT subject(usrSn) 기반 조회 - 필터 인가·토큰 재발급이 가변 필드(email) 대신 사용
    @Override
    @Transactional(readOnly = true)
    public Optional<AuthMember> findById(Long usrSn) {
        return memberMapper.findMemberById(usrSn)
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

    // @ai_generated: 작업단위5 - POL-AUTH-010. OAUTH_+ULID 시스템 로그인ID·CSPRNG+BCrypt 비밀번호로
    // USERS를 만들고 같은 트랜잭션에서 USER_OAUTH 연동 행을 저장한다. 이메일/닉네임 중복은 사전 확인
    // 없이(온보딩 화면 제출 시점 호출이라 이 계층에서 사전 확인이 불가) DataIntegrityViolationException을
    // 그대로 던진다 - 호출측(작업단위5 온보딩: OauthOnboardingService.duplicateException)이 변환한다.
    @Override
    @Transactional
    public AuthMember registerOAuthMember(OAuthProfile profile) {
        Member member = Member.builder()
            .usrLoginId("OAUTH_" + generateUlid())
            .usrPswdHash(passwordEncoder.encode(generateSystemPassword()))
            .usrNm(profile.getNickname())
            .usrEml(profile.getEmail())
            .usrEmlCertYn('Y')          // 제공자가 이미 검증한 이메일로 간주 (자체 EMAIL_VERIFICATION 재검증 없음)
            .usrStatusCd("USRC0001")    // 신규가입 기본 상태 (seed 기준: 활성/기본 정상 회원)
            .usrRoleCd("ROLE_USER")     // 신규가입 기본 역할
            .build();
        memberMapper.saveCertifiedMember(member);   // useGeneratedKeys 로 usrSn 채워짐
        userOauthMapper.insert(member.getUsrSn(), profile.getProvider(), profile.getProviderKey());
        return toAuthMember(member);
    }

    // @ai_generated: 작업단위5 - USER_OAUTH(provider, providerKey) 기준 기존 연동 회원 조회.
    // ISS-001/설계 결정(A) - 로그인용 email 대신 이 조회로 재로그인 시 기존 회원을 찾는다.
    @Override
    @Transactional(readOnly = true)
    public Optional<AuthMember> findByOauthProviderKey(String providerCd, String providerKey) {
        return userOauthMapper.findUsrSnByProviderAndKey(providerCd, providerKey)
                              .flatMap(memberMapper::findMemberById)
                              .map(this::toAuthMember);
    }

    // @ai_generated: 작업단위5 - Crockford Base32 ULID(48비트 타임스탬프 + 80비트 CSPRNG 랜덤 = 128비트/26자).
    // 신규 라이브러리 의존성 추가 없이 자체 구현 (POL-AUTH-010은 형식만 요구, 특정 라이브러리를 요구하지 않음).
    private String generateUlid() {
        long timestamp = System.currentTimeMillis();
        byte[] data = new byte[16];
        data[0] = (byte) (timestamp >>> 40);
        data[1] = (byte) (timestamp >>> 32);
        data[2] = (byte) (timestamp >>> 24);
        data[3] = (byte) (timestamp >>> 16);
        data[4] = (byte) (timestamp >>> 8);
        data[5] = (byte) timestamp;
        byte[] entropy = new byte[10];
        secureRandom.nextBytes(entropy);
        System.arraycopy(entropy, 0, data, 6, 10);
        return encodeCrockfordBase32(data);
    }

    private String encodeCrockfordBase32(byte[] data) {
        StringBuilder sb = new StringBuilder(26);
        long buffer = 0;
        int bitsInBuffer = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFFL);
            bitsInBuffer += 8;
            while (bitsInBuffer >= 5) {
                bitsInBuffer -= 5;
                int index = (int) ((buffer >>> bitsInBuffer) & 0x1F);
                sb.append(CROCKFORD_BASE32[index]);
            }
        }
        if (bitsInBuffer > 0) {
            int index = (int) ((buffer << (5 - bitsInBuffer)) & 0x1F);
            sb.append(CROCKFORD_BASE32[index]);
        }
        return sb.toString();
    }

    // @ai_generated: 작업단위5 - POL-AUTH-010 CSPRNG 원문. PasswordResetService의 토큰 생성 패턴과 동일.
    // 원문은 즉시 BCrypt 인코딩 후 버려지며 어디에도 저장·노출되지 않는다 - 사용자 로그인 수단이 아니라
    // USERS.USR_PSWD_HASH NOT NULL 제약을 만족시키기 위한 값이다.
    private String generateSystemPassword() {
        byte[] tokenBytes = new byte[SYSTEM_PASSWORD_BYTES];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getEncoder().encodeToString(tokenBytes);
    }

    // @ai_generated: 저장 전 SHA-256 해시화(원문 저장 금지). null(로그아웃) 은 해시하지 않고 그대로 저장.
    @Override
    @Transactional
    public void updateRefreshToken(Long usrSn, String refreshToken) {
        String refreshTokenHash = (refreshToken == null) ? null : tokenHashUtil.hash(refreshToken);
        // 검증(AuthService.verifyRefreshToken)에서 요청 토큰을 동일하게 해시화한 뒤 대조 + JwtProvider 로 서명/만료 확인.
        memberMapper.updateRefreshTokenById(usrSn, refreshTokenHash);
    }

    // @ai_generated: CHG-032/F-PROV-015 - AuthService가 정한 현재 활성 ROLE을 USERS에 반영한다.
    @Override
    @Transactional
    public int updateRole(Long usrSn, String role) {
        return memberMapper.updateRoleById(usrSn, role);
    }

    // @ai_generated: F-AUTH-007 - 비밀번호 재설정 완료 시 호출. 인코딩은 PasswordResetService(BCrypt)에서 이미 완료됨.
    @Override
    @Transactional
    public void updatePassword(Long usrSn, String encodedPassword) {
        memberMapper.updatePasswordById(usrSn, encodedPassword);
    }

    /** Member(도메인) -> AuthMember(보안 모듈 전용 모델) 변환 */
    private AuthMember toAuthMember(Member member) {
        // @ai_generated: USERS.USR_NM은 정본의 닉네임이며 AuthMember의 name/nickname 응답에 같은 값을 준다.
        return AuthMember.builder()
                         .id(member.getUsrSn())
                         .loginId(member.getUsrLoginId())
                         .email(member.getUsrEml())
                         .password(member.getUsrPswdHash())
                         .name(member.getUsrNm())
                         .nickname(member.getUsrNm())
                         .role(member.getUsrRoleCd())
                         .status(member.getUsrStatusCd())  // @ai_generated: F-AUTH-009 계정 상태 차단용
                         .refreshToken(member.getUsrRefreshTokenHash())  // DB 저장 해시(재발급 검증용)
                         .build();
    }
}
