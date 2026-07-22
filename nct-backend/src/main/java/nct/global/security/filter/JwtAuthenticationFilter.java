package nct.global.security.filter;

import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.ApiResponse;
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
 * - @ai_generated: 정지/탈퇴 계정(F-AUTH-009)은 "미인증 통과"가 아니라 필터가 즉시 403/410을
 *   직접 응답하고 체인을 끊는다 - 컨트롤러까지 도달시키지 않는다(GlobalExceptionHandler는
 *   서블릿 필터 예외를 잡지 못하므로 여기서 직접 처리해야 한다).
 */

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final CookieUtil cookieUtil;
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final ObjectMapper objectMapper;

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

        // @ai_generated: CHG-032 - subject 파싱 실패(구버전 토큰)·회원 조회 실패
        //   (F-AUTH-009 정지/탈퇴 포함)를 하나의 catch로 묶어 500 대신
        //   일관된 ApiResponse JSON(401/403/410)으로 응답한다.
        UserDetails userDetails;
        try {
            Long usrSn = jwtTokenProvider.getUsrSn(accessToken);
            userDetails = customUserDetailsService.loadUserByUsername(String.valueOf(usrSn));
        } catch (CustomException ex) {
            writeErrorResponse(response, ex.getErrorCode(), requestURI);
            return;
        }

        // @ai_generated: CHG-032 - 토큰은 회원 식별·서명 검증에만 사용하고,
        // 현재 접근 권한은 방금 DB에서 읽은 USR_ROLE_CD 하나(userDetails authorities)로 구성한다.
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

    // @ai_generated: SecurityConfig.writeErrorResponse와 동일한 ApiResponse JSON 포맷 + code 필드 포함
    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode, String path)
            throws IOException {
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter()
                .write(objectMapper.writeValueAsString(
                        ApiResponse.error(errorCode.code(), errorCode.message(), path, errorCode.name())));
    }
}
