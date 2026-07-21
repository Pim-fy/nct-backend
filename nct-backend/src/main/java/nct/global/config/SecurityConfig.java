package nct.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.global.security.filter.JwtAuthenticationFilter;
import nct.global.security.handler.OAuth2FailureHandler;
import nct.global.security.handler.OAuth2LinkFailureHandler;
import nct.global.security.handler.OAuth2LinkSuccessHandler;
import nct.global.security.handler.OAuth2SuccessHandler;
import nct.global.security.provider.JwtTokenProvider;
import nct.global.security.service.CustomOAuth2UserService;
import nct.global.security.service.CustomUserDetailsService;
import nct.global.security.service.OAuthLinkUserService;
import nct.global.utils.CookieUtil;
import nct.ops.security.service.SensitiveDataMasker;

/**
 * [설정 - Spring Security]
 * - STATELESS : 세션을 만들지 않음 (JWT 기반 인증)
 * - permit-all 경로는 SecurityProperties(프로퍼티)에서 주입
 *   : 기본 정책 = "명시된 경로 외 전부 인증 필요" (화이트리스트 방식)
 * - 401/403 응답을 ApiResponse JSON 포맷으로 통일
 *   : 원본(sendError)의 HTML 에러 응답 문제를 개선
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityProperties securityProperties;
    private final CorsConfigurationSource corsConfigurationSource;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;
    // @ai_generated: 작업단위5 작업 2 - 계정 연동(link) 전용, 로그인용 Bean과 완전히 분리
    private final OAuthLinkUserService oAuthLinkUserService;
    private final OAuth2LinkSuccessHandler oAuth2LinkSuccessHandler;
    private final OAuth2LinkFailureHandler oAuth2LinkFailureHandler;
    private final ObjectMapper objectMapper;
    private final CookieUtil cookieUtil;
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    // Controller까지 도달하지 못한 401·403 응답 경로도 F-OPS-012 규칙으로 마스킹한다.
    private final SensitiveDataMasker sensitiveDataMasker;

    // @ai_generated: 작업단위5 작업 2 - SPEC 설계 결정(F) - "연동" 전용 콜백(*-link)만 매칭하는 별도
    // SecurityFilterChain. 로그인 체인(@Order(2), 아래)보다 먼저 평가돼야 하므로 @Order(1).
    // 이 체인은 permitAll로 두고, "누구에게 연동할지" 식별은 OAuthLinkUserService 내부의 JWT 쿠키
    // 직접 검사가 담당한다(필터 체인의 authorizeHttpRequests에 의존하지 않음 - 폴백 없는 명확한 실패를
    // 위해 검증 책임을 한 곳에 모은다).
    @Bean
    @Order(1)
    public SecurityFilterChain oauthLinkFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/oauth2/authorization/*-link", "/api/login/oauth2/code/*-link")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(endpoint -> endpoint
                    .baseUri("/api/oauth2/authorization"))
                .redirectionEndpoint(endpoint -> endpoint
                    .baseUri("/api/login/oauth2/code/*"))
                .userInfoEndpoint(endpoint -> endpoint
                    .userService(oAuthLinkUserService))
                .successHandler(oAuth2LinkSuccessHandler)
                .failureHandler(oAuth2LinkFailureHandler)
            );

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // JWT 기반이므로 CSRF 비활성화 (쿠키 SameSite=Lax 로 보완)
            .csrf(csrf -> csrf.disable())
            // 세션 미사용 - 모든 인증은 JWT 로
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .authorizeHttpRequests(auth -> auth
                // 에러 페이지 포워딩 시 인증 블락(403) 방지
                .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                // 관리자 API
                .requestMatchers("/api/admin/**")
                    .hasAuthority("ROLE_ADMIN")
                // F-COM-003: 가입 전 서비스 탐색에서도 활성 카테고리 목록은 조회할 수 있다.
                .requestMatchers(HttpMethod.GET, "/api/categories")
                    .permitAll()
                // 담당자 7 · F-COM-013: 방문자도 게시 중인 공지 목록·상세를 조회할 수 있다.
                // 쓰기 API는 /api/admin/** 아래에 분리되어 있어 이 규칙으로 공개되지 않는다.
                .requestMatchers(HttpMethod.GET, "/api/notices", "/api/notices/**")
                    .permitAll()
                // 경매 목록·상세는 비로그인 사용자도 탐색할 수 있다.
                .requestMatchers(HttpMethod.GET, "/api/auctions", "/api/auctions/*")
                    .permitAll()
                // 첨부파일 서빙(WebConfig 정적 핸들러) - 상품 이미지는 비로그인 탐색에서도 보여야 한다.
                //   업로드/삭제/교체(POST·DELETE·PUT)는 인증 필요라 GET만 연다.
                //   (properties의 permit-all-paths는 HTTP 메서드 구분이 없어 여기 Java에서 지정)
                //   ⚠️ 공개는 product 경로만 - provider(제공자 서류)는 민감정보라 공개 서빙 금지,
                //   관리자 전용 API(/api/admin/provider-applications/**, 위 ROLE_ADMIN 규칙)로만 열람 (2026-07-20)
                .requestMatchers(HttpMethod.GET, "/api/attachment/product/**")
                    .permitAll()
                // 화이트리스트 - application.properties 의 app.security.permit-all-paths
                .requestMatchers(securityProperties.getPermitAllPaths().toArray(String[]::new))
                    .permitAll()
                // 그 외 전부 인증 필요
                .anyRequest()
                    .authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                // 프론트 호출 예: location.href = "http://localhost:8080/api/oauth2/authorization/kakao"
                .authorizationEndpoint(endpoint -> endpoint
                    .baseUri("/api/oauth2/authorization"))
                // 카카오 인증 완료 후 인가 코드가 돌아오는 백엔드 도착점
                .redirectionEndpoint(endpoint -> endpoint
                    .baseUri("/api/login/oauth2/code/*"))
                .userInfoEndpoint(endpoint -> endpoint
                    .userService(customOAuth2UserService))
                .successHandler(oAuth2SuccessHandler)
                // @ai_generated: 작업단위5 - 미등록 시 Spring Security 기본값(백엔드 "/login?error")으로
                // 새서 프론트가 실패 사유를 받지 못했다. CustomOAuth2UserService가 던지는
                // OAuth2AuthenticationException을 여기로 받아 프론트로 안전하게 리다이렉트한다.
                .failureHandler(oAuth2FailureHandler)
            )
            // 401/403 을 ApiResponse JSON 으로 응답 (REST API 표준화)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                    writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                                       "인증이 필요합니다.", request.getRequestURI()))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                                       "접근 권한이 없습니다.", request.getRequestURI()))
            )
            // JWT 필터를 폼 로그인 필터 앞에 배치
            .addFilterBefore(new JwtAuthenticationFilter(cookieUtil, jwtTokenProvider, customUserDetailsService, objectMapper),
                             UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // 보안 예외를 ApiResponse JSON 으로 직렬화해 응답
    private void writeErrorResponse(HttpServletResponse response, int status,
                                    String message, String path) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter()
                .write(objectMapper.writeValueAsString(
                        ApiResponse.error(status, message, sensitiveDataMasker.maskUri(path))));
    }
}
