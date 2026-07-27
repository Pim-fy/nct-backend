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

// @ai_generated: 작업단위5(F-AUTH-004 온보딩) - 소셜 최초 가입 시 제공자 콜백에서 파싱한 정보를
// DB에 저장하지 않고(ISS-009/POL-AUTH-015 - 동의 전엔 회원 데이터 저장 안 함) 온보딩 제출 시점까지
// 짧게 들고 있기 위한 자체 서명 토큰. JwtTokenProvider(로그인 세션 토큰 전용, 이미 검증된 코드)는
// 그대로 두고 별도 클래스로 분리했다 - 같은 jwt.secret을 재사용하지만 클레임 모양과 목적이 완전히
// 달라(세션 토큰 vs "제공자가 이 사람 신원을 검증했다"는 임시 확인서) 섞으면 두 개념이 혼동된다.
/**
 * [OAuth 온보딩 임시 토큰]
 * - 만료가 짧고(10분) DB에 아무 흔적을 남기지 않는 Stateless 토큰이다.
 * - 이탈해도 정리할 데이터가 없다 - EMAIL_VERIFICATION류의 만료 정리 배치가 필요 없다.
 */
@Component
public class OAuthOnboardingTokenProvider {

    private static final long EXPIRY_MILLIS = 10 * 60 * 1000L; // 10분

    @Value("${jwt.secret}")
    private String secretKey;

    public record OnboardingClaims(String providerCd, String providerKey, String email, String nickname) {
    }

    /** 제공자 콜백에서 파싱한 정보를 담은 온보딩 토큰 발급 */
    public String createToken(String providerCd, String providerKey, String email, String nickname) {
        return Jwts.builder()
                   .subject(providerKey)
                   .claim("providerCd", providerCd)
                   .claim("email", email)
                   .claim("nickname", nickname)
                   .issuedAt(new Date())
                   .expiration(new Date(System.currentTimeMillis() + EXPIRY_MILLIS))
                   .signWith(getSigningKey())
                   .compact();
    }

    /** 온보딩 토큰 검증 + 클레임 추출 */
    public OnboardingClaims parseToken(String token) {
        Claims claims = parseClaims(token);
        return new OnboardingClaims(
            claims.get("providerCd", String.class),
            claims.getSubject(),
            claims.get("email", String.class),
            claims.get("nickname", String.class));
    }

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

    private SecretKey getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
