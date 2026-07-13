package nct.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import nct.auth.dto.LoginRequest;
import nct.auth.dto.LoginResponse;
import nct.auth.dto.SignUpRequest;
import nct.auth.service.AuthService;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.global.security.port.AuthMember;

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

    /** 회원가입 */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<LoginResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        return ResponseEntity.status(201)
                             .body(ApiResponse.created(authService.signUp(request)));
    }

    /** 로그인 - 토큰은 httpOnly 쿠키로, 본문에는 사용자 정보만 */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login( @Valid @RequestBody LoginRequest request
    														, HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request, response)));
    }

    /** Access Token 재발급 (Refresh 쿠키 필요) */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh( HttpServletRequest request
    												 , HttpServletResponse response) {
        authService.refresh(request, response);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 새로고침 자동 로그인 - 프론트 전역 상태 복원용 */
    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<LoginResponse>> verify( HttpServletRequest request
    														 , HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.success(authService.verifyAndRefresh(request, response)));
    }

    /** 로그아웃 - Refresh 무효화 + 쿠키 삭제 */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout( @AuthenticationPrincipal CustomUserDetails userDetails
    												, HttpServletResponse response) {
        authService.logout(userDetails.getMember().getId(), response);
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
}
