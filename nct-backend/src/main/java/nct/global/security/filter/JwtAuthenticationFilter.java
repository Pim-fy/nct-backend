package nct.global.security.filter;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import nct.global.security.provider.JwtTokenProvider;
import nct.global.security.service.CustomUserDetailsService;
import nct.global.utils.CookieUtil;
import lombok.RequiredArgsConstructor;

/**
 * [JWT 인증 필터]
 * - 매 요청마다 httpOnly 쿠키에서 Access Token 을 꺼내 검증
 * - 유효하면 SecurityContext 에 인증 정보 저장
 *   : 이후 @AuthenticationPrincipal 로 사용자 정보 접근 가능
 * - 토큰이 없거나 무효면 "미인증 상태"로 통과
 *   : 최종 허용/거부는 SecurityConfig 의 인가 규칙이 결정
 */

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final CookieUtil cookieUtil;
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // OAuth2 로그인 경로는 JWT 검증 없이 통과 (전용 필터가 처리)
        String requestURI = request.getRequestURI();
        if (requestURI.startsWith("/api/oauth2/") || requestURI.startsWith("/api/login/oauth2/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // httpOnly 쿠키에서 Access Token 추출
        String accessToken = cookieUtil.extractCookie(request, CookieUtil.ACCESS_TOKEN_COOKIE);

        // 토큰 없음/무효 -> 미인증 상태로 다음 필터 진행
        if (!StringUtils.hasText(accessToken) || !jwtTokenProvider.validateToken(accessToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 이미 인증된 요청이면 중복 처리 방지
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 토큰에서 이메일 추출 -> DB 에서 사용자 조회
        String email = jwtTokenProvider.getEmail(accessToken);
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);

        // 인증 객체 생성 (credentials 는 JWT 방식에서 불필요 -> null)
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());

        // 요청 정보(IP 등)를 인증 객체에 추가
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
