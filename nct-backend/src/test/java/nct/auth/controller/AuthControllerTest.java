package nct.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletResponse;

import nct.auth.dto.LoginResponse;
import nct.auth.dto.LoginRequest;
import nct.auth.service.AuthService;
import nct.auth.service.AuthSessionResult;
import nct.auth.service.EmailVerificationService;
import nct.auth.service.OauthOnboardingService;
import nct.auth.service.PasswordResetService;
import nct.global.security.domain.CustomUserDetails;
import nct.global.security.port.AuthMember;
import nct.global.utils.CookieUtil;

// @ai_generated CHG-032/F-PROV-015: controller가 갱신 access cookie와 현재 ROLE 응답을 함께 주는지 검증한다.
class AuthControllerTest {

    @Test
    void 관리자_로그인은_전용_서비스를_호출하고_인증쿠키를_발급한다() {
        AuthService authService = mock(AuthService.class);
        CookieUtil cookieUtil = mock(CookieUtil.class);
        AuthController controller = new AuthController(
                authService,
                mock(EmailVerificationService.class),
                mock(PasswordResetService.class),
                mock(OauthOnboardingService.class),
                cookieUtil);
        LoginRequest request = new LoginRequest();
        request.setLoginId("admin01");
        request.setPassword("Password1!");
        request.setRememberMe(true);
        LoginResponse loginResponse = LoginResponse.builder().id(101L).role("ROLE_ADMIN").build();
        AuthSessionResult session = AuthSessionResult.builder()
                .accessToken("admin-access-token")
                .refreshToken("admin-refresh-token")
                .loginResponse(loginResponse)
                .build();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authService.adminLogin(request)).thenReturn(session);
        when(cookieUtil.createAccessTokenCookie("admin-access-token"))
                .thenReturn(ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "admin-access-token").build());
        when(cookieUtil.createRefreshTokenCookie("admin-refresh-token", true))
                .thenReturn(ResponseCookie.from(CookieUtil.REFRESH_TOKEN_COOKIE, "admin-refresh-token").build());

        var result = controller.adminLogin(request, response);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData().getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).hasSize(2);
        verify(authService).adminLogin(request);
        verify(cookieUtil).createRefreshTokenCookie("admin-refresh-token", true);
    }

    @Test
    void 모드전환_성공시_새_AccessCookie와_갱신된_ROLE을_반환한다() {
        AuthService authService = mock(AuthService.class);
        CookieUtil cookieUtil = mock(CookieUtil.class);
        AuthController controller = new AuthController(
                authService,
                mock(EmailVerificationService.class),
                mock(PasswordResetService.class),
                mock(OauthOnboardingService.class),
                cookieUtil);

        AuthMember member = AuthMember.builder().id(101L).email("user@example.com").role("ROLE_USER").build();
        LoginResponse loginResponse = LoginResponse.builder().id(101L).role("ROLE_SERVICE").build();
        AuthSessionResult session = AuthSessionResult.builder()
                .accessToken("new-access-token")
                .loginResponse(loginResponse)
                .build();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authService.switchMode(101L, "SERVICE")).thenReturn(session);
        when(cookieUtil.createAccessTokenCookie("new-access-token"))
                .thenReturn(ResponseCookie.from(CookieUtil.ACCESS_TOKEN_COOKIE, "new-access-token").build());

        var result = controller.switchMode(new CustomUserDetails(member), "SERVICE", response);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData().getRole()).isEqualTo("ROLE_SERVICE");
        assertTrue(response.getHeaders(HttpHeaders.SET_COOKIE).getFirst()
                .contains(CookieUtil.ACCESS_TOKEN_COOKIE + "=new-access-token"));
        verify(authService).switchMode(101L, "SERVICE");
    }
}
