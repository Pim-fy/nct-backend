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
 *   : Payload   - subject(이메일), role, iat(발급시각), exp(만료시각)
 *   : Signature - 시크릿 키 서명 -> 위변조 방지
 * - Access Token  : 만료 짧음 (기본 30분), API 인증에 사용
 * - Refresh Token : 만료 김 (기본 1일), Access Token 재발급에만 사용
 */
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    /**
     * Access Token 생성
     * @param email 토큰 subject 에 저장할 이메일
     * @param role  커스텀 클레임으로 저장할 권한 (예: "ROLE_USER")
     */
    public String createAccessToken(String email, String role) {
        return Jwts.builder()
                   .subject(email)
                   .claim("role", role)
                   .issuedAt(new Date())
                   .expiration(new Date(System.currentTimeMillis() + accessTokenExpiry))
                   .signWith(getSigningKey())
                   .compact();
    }

    /**
     * Refresh Token 생성 (권한 미포함 - 재발급 전용)
     */
    public String createRefreshToken(String email) {
        return Jwts.builder()
                   .subject(email)
                   .issuedAt(new Date())
                   .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiry))
                   .signWith(getSigningKey())
                   .compact();
    }

    /** 토큰에서 이메일(subject) 추출 */
    public String getEmail(String token) {
        return parseClaims(token).getSubject();
    }

    /** 토큰에서 권한 추출 */
    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
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
