package nct.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import nct.auth.dto.LoginRequest;
import nct.auth.dto.LoginResponse;
import nct.auth.dto.SignUpRequest;
import nct.auth.dto.AvailabilityResponse;
import nct.auth.dto.EmailVerificationSendRequest;
import nct.auth.dto.EmailVerificationSendResponse;
import nct.auth.dto.EmailVerificationVerifyRequest;
import nct.auth.service.AuthService;
import nct.auth.service.AuthSessionResult;
import nct.auth.service.EmailVerificationService;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.global.security.port.AuthMember;
import nct.global.utils.CookieUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * [인증 API 목록]
 *
 *  POST /api/auth/signup   회원가입                    (permitAll)
 *  POST /api/auth/login    로그인 -> JWT httpOnly 쿠키  (permitAll)
 *  POST /api/auth/refresh  Access Token 재발급          (permitAll - Refresh 쿠키로 검증)
 *  GET  /api/auth/verify   새로고침 자동 로그인          (permitAll - Refresh 쿠키로 검증)
 *  POST /api/auth/logout   로그아웃                     (authenticated)
 *  GET  /api/auth/me       내 정보                      (authenticated)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final CookieUtil cookieUtil;

    /** 회원가입 */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<LoginResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        return ResponseEntity.status(201)
                             .body(ApiResponse.created(authService.signUp(request)));
    }

    // @ai_generated: 기존 프론트의 중복 확인 URL은 유지하고 로그인 ID 확인만 추가한다.
    @GetMapping("/check-login-id")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> checkLoginId(
            @RequestParam String loginId) {
        return ResponseEntity.ok(ApiResponse.success(authService.checkLoginId(loginId)));
    }

    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> checkEmail(@RequestParam String email) {
        return ResponseEntity.ok(ApiResponse.success(authService.checkEmail(email)));
    }

    @GetMapping("/check-nickname")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> checkNickname(@RequestParam String nickname) {
        return ResponseEntity.ok(ApiResponse.success(authService.checkNickname(nickname)));
    }

    // @ai_generated: 필수 약관 동의가 확인된 SIGNUP 인증번호 발송 API다.
    @PostMapping("/email-verifications")
    public ResponseEntity<ApiResponse<EmailVerificationSendResponse>> sendEmailVerification(
            @Valid @RequestBody EmailVerificationSendRequest request) {
        return ResponseEntity.status(201)
                             .body(ApiResponse.created(emailVerificationService.sendSignupCode(request)));
    }

    @PostMapping("/email-verifications/{verificationId}/verify")
    public ResponseEntity<ApiResponse<Void>> verifyEmailVerification(
            @PathVariable Long verificationId,
            @Valid @RequestBody EmailVerificationVerifyRequest request) {
        emailVerificationService.verifySignupCode(verificationId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 로그인 - 토큰은 httpOnly 쿠키로, 본문에는 사용자 정보만 */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletResponse response) {
        AuthSessionResult session = authService.login(request);
        writeLoginCookies(response, session, request.isRememberMe());
        return ResponseEntity.ok(ApiResponse.success(session.getLoginResponse()));
    }

    /** Access Token 재발급 (Refresh 쿠키 필요) */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(HttpServletRequest request,
                                                      HttpServletResponse response) {
        String refreshToken = cookieUtil.extractCookie(request, CookieUtil.REFRESH_TOKEN_COOKIE);
        String accessToken = authService.refresh(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtil.createAccessTokenCookie(accessToken).toString());
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 새로고침 자동 로그인 - 프론트 전역 상태 복원용 */
    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<LoginResponse>> verify(HttpServletRequest request,
                                                              HttpServletResponse response) {
        String refreshToken = cookieUtil.extractCookie(request, CookieUtil.REFRESH_TOKEN_COOKIE);
        AuthSessionResult session = authService.verifyAndRefresh(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.createAccessTokenCookie(session.getAccessToken()).toString());
        return ResponseEntity.ok(ApiResponse.success(session.getLoginResponse()));
    }

    /** 로그아웃 - Refresh 무효화 + 쿠키 삭제 */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                     HttpServletResponse response) {
        authService.logout(userDetails.getMember().getId());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtil.deleteAccessTokenCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtil.deleteRefreshTokenCookie().toString());
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 내 정보 - JWT 인증 필요 */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<LoginResponse>> me(@AuthenticationPrincipal CustomUserDetails userDetails) {
        AuthMember member = userDetails.getMember();
        return ResponseEntity.ok(ApiResponse.success(
                LoginResponse.builder()
                             .id(member.getId())
                             .email(member.getEmail())
                             .name(member.getName())
                             .nickname(member.getNickname())
                             .role(member.getRole())
                             .provider(member.getProvider())
                             .build()));
    }

    // @ai_generated: HttpOnly 쿠키 생성은 Web 계층에만 두고 Service에는 HTTP 타입을 전달하지 않는다.
    private void writeLoginCookies(HttpServletResponse response, AuthSessionResult session, boolean rememberMe) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtil.createAccessTokenCookie(session.getAccessToken()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieUtil.createRefreshTokenCookie(session.getRefreshToken(), rememberMe).toString());
    }
}
