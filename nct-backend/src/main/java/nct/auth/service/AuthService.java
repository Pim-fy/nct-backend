package nct.auth.service;

import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nct.auth.dto.LoginRequest;
import nct.auth.dto.LoginResponse;
import nct.auth.dto.SignUpRequest;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.global.security.port.LocalSignUpProfile;
import nct.global.security.provider.JwtTokenProvider;
import nct.global.utils.CookieUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * [인증 서비스]
 * - 회원 도메인을 직접 알지 못하고 AuthMemberPort(포트)로만 접근
 *   : 다른 프로젝트 이식 시 포트 구현체만 바꾸면 이 클래스는 수정 불필요
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final AuthMemberPort authMemberPort;
    private final JwtTokenProvider jwtTokenProvider;
    private final CookieUtil cookieUtil;

    /**
     * 회원가입
     * - 이메일 중복 확인 후 BCrypt 인코딩해 포트로 전달
     */
    @Transactional
    public LoginResponse signUp(SignUpRequest request) {
        authMemberPort.findByEmail(request.getEmail())
                      .ifPresent(member -> {
                          throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
                      });

        AuthMember member = authMemberPort.registerLocalMember(
                LocalSignUpProfile.builder()
                                  .email(request.getEmail())
                                  .encodedPassword(passwordEncoder.encode(request.getPassword()))
                                  .name(request.getName())
                                  .nickname(request.getNickname())
                                  .telno(request.getTelno())
                                  .build());

        return toLoginResponse(member);
    }

    /**
     * 로그인
     * - 비밀번호 검증 -> JWT 발급 -> Refresh DB 저장 -> httpOnly 쿠키 탑재
     * - 실패 사유(사용자 없음/비밀번호 불일치)를 구분하지 않고
     *   동일한 INVALID_CREDENTIALS 로 응답 (계정 존재 여부 노출 방지)
     */
    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        AuthMember member = authMemberPort.findByEmail(request.getEmail())
                                          .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        if (member.getPassword() == null
                || !passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken  = jwtTokenProvider.createAccessToken(member.getEmail(), member.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getEmail());

        authMemberPort.updateRefreshToken(member.getId(), refreshToken);

        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.createAccessTokenCookie(accessToken).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.createRefreshTokenCookie(refreshToken, request.isRememberMe()).toString());

        return toLoginResponse(member);
    }

    /**
     * Access Token 재발급
     * - Refresh 쿠키 -> JWT 검증 -> DB 저장값과 비교(탈취 토큰 재사용 방지) -> 새 Access 발급
     */
    @Transactional(readOnly = true)
    public void refresh(HttpServletRequest request, HttpServletResponse response) {
        AuthMember member = verifyRefreshToken(request);

        String newAccessToken =
                jwtTokenProvider.createAccessToken(member.getEmail(), member.getRole());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.createAccessTokenCookie(newAccessToken).toString());
    }

    /**
     * 새로고침 자동 로그인
     * - refresh 와 동일한 검증 후 사용자 정보까지 반환
     *   : 프론트엔드 전역 상태(Context) 복원용
     */
    @Transactional(readOnly = true)
    public LoginResponse verifyAndRefresh(HttpServletRequest request, HttpServletResponse response) {
        AuthMember member = verifyRefreshToken(request);

        String newAccessToken =
                jwtTokenProvider.createAccessToken(member.getEmail(), member.getRole());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.createAccessTokenCookie(newAccessToken).toString());

        return toLoginResponse(member);
    }

    /**
     * 로그아웃
     * - DB Refresh Token 무효화 + 쿠키 2종 삭제 (완전한 로그아웃)
     */
    @Transactional
    public void logout(Long memberId, HttpServletResponse response) {
        authMemberPort.updateRefreshToken(memberId, null);

        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.deleteAccessTokenCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.deleteRefreshTokenCookie().toString());
    }

    /** Refresh 쿠키 추출 -> JWT 검증 -> DB 저장 토큰과 대조 */
    private AuthMember verifyRefreshToken(HttpServletRequest request) {
        String refreshToken = cookieUtil.extractCookie(request, CookieUtil.REFRESH_TOKEN_COOKIE);
        if (refreshToken == null) {
            throw new CustomException(ErrorCode.TOKEN_NOT_FOUND);
        }

        // 만료/위조 시 CustomException(EXPIRED_TOKEN/INVALID_TOKEN) 발생
        String email = jwtTokenProvider.getEmail(refreshToken);

        AuthMember member = authMemberPort.findByEmail(email)
                                          .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // DB 저장 원문과 대조 (JWT 서명·만료는 위 getEmail 단계에서 이미 검증됨)
        // 저장값이 null(로그아웃 상태)이거나 다르면 탈취/이전 토큰 -> 거부
        if (member.getRefreshToken() == null
                || !refreshToken.equals(member.getRefreshToken())) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        return member;
    }

    private LoginResponse toLoginResponse(AuthMember member) {
        return LoginResponse.builder()
                            .id(member.getId())
                            .email(member.getEmail())
                            .name(member.getName())
                            .nickname(member.getNickname())
                            .role(member.getRole())
                            .provider(member.getProvider())
                            .build();
    }
}
