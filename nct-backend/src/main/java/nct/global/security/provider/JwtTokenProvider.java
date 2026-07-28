package nct.global.security.provider;

import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

/**
 * [JWT 발급/검증]
 * - JWT 구조: Header.Payload.Signature
 *   : Header    - 알고리즘 정보 (HS256)
 *   : Payload   - subject(회원일련번호 USR_SN), iat(발급시각), exp(만료시각)
 *   : Signature - 시크릿 키 서명 -> 위변조 방지
 * - Access Token  : 만료 짧음 (기본 30분), API 인증에 사용
 * - Refresh Token : 만료 김 (기본 1일, 로그인 유지 체크 시에도 1일이지만 CookieUtil이 영속 쿠키로
 *   저장해 브라우저 재시작에도 살아남는다 - 미체크는 세션 쿠키라 브라우저 종료 시 먼저 끊긴다),
 *   Access Token 재발급에만 사용
 *
 * @ai_generated: subject 는 email(가변 - 프로필 수정으로 변경 가능)이 아닌 USR_SN(불변 PK)을 사용한다.
 *   email/nickname 은 F-AUTH-010 프로필 수정으로 변경될 수 있어 세션 식별자로 부적합하다.
 */
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    // @ai_generated: 로그인 유지(rememberMe) 버그 수정 - 프론트 문구("하루동안 로그인 유지", LoginPage.jsx)
    // 기준으로 1일(밀리초). 기존엔 이 토큰의 실제 만료가 rememberMe와 무관하게 항상 refreshTokenExpiry
    // 고정이라, 로그인 유지를 체크해도 쿠키만 살아있고 토큰 자체는 하루 뒤 EXPIRED_TOKEN으로 거부됐다.
    // 미체크 시엔 CookieUtil이 세션 쿠키(브라우저 종료 시 즉시 소멸)로 처리하므로 이 값보다 먼저 끊기는 게 보통이다.
    @Value("${jwt.refresh-token-expiry-remember-me:86400000}")
    private long refreshTokenExpiryRememberMe;

    /**
     * Access Token 생성
     * @param usrSn 토큰 subject 에 저장할 회원일련번호(불변 PK)
     */
    public String createAccessToken(Long usrSn) {
        return Jwts.builder()
                   .subject(String.valueOf(usrSn))
                   .issuedAt(new Date())
                   .expiration(new Date(System.currentTimeMillis() + accessTokenExpiry))
                   .signWith(getSigningKey())
                   .compact();
    }

    /**
     * Refresh Token 생성 (권한 미포함 - 재발급 전용)
     * @param rememberMe true면 CookieUtil의 쿠키 유지기간(기본 14일)과 맞춘 만료를,
     *                   false면 기존 기본 만료(refreshTokenExpiry)를 사용한다.
     */
    public String createRefreshToken(Long usrSn, boolean rememberMe) {
        long expiry = rememberMe ? refreshTokenExpiryRememberMe : refreshTokenExpiry;
        return Jwts.builder()
                   .subject(String.valueOf(usrSn))
                   .issuedAt(new Date())
                   .expiration(new Date(System.currentTimeMillis() + expiry))
                   .signWith(getSigningKey())
                   .compact();
    }

    // @ai_generated: 레드팀 3-A 대응 - subject가 숫자 형식이 아니면(예: 구버전 email subject 토큰)
    //   NumberFormatException을 그대로 전파하지 않고 INVALID_TOKEN으로 변환한다. 서명 유효 토큰이라도
    //   subject 형식이 이 시스템의 계약과 다르면 "무효한 토큰"과 동일하게 처리해야 500을 피할 수 있다.
    /** 토큰에서 회원일련번호(subject) 추출 */
    public Long getUsrSn(String token) {
        try {
            return Long.valueOf(parseClaims(token).getSubject());
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    /** 토큰 유효성 검증 (만료/서명/형식) */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (CustomException e) {
            return false;
        }
    }

    /**
     * 토큰 파싱 + 서명 검증
     * @throws CustomException EXPIRED_TOKEN(만료) / INVALID_TOKEN(서명·형식 오류)
     */
    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                       .verifyWith(getSigningKey())
                       .build()
                       .parseSignedClaims(token)
                       .getPayload();
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    /**
     * Base64 인코딩된 시크릿 키 -> HMAC-SHA 서명 키
     * - 256비트(32바이트) 이상이어야 HS256 서명 가능
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
