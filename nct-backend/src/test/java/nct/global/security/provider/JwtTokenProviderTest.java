package nct.global.security.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

// @ai_generated
/** 레드팀 3-A 대응 검증: subject가 usrSn(숫자) 형식이 아닌 구버전 토큰을 INVALID_TOKEN으로 차단하는지 확인한다. */
class JwtTokenProviderTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(
            "test-secret-key-at-least-32-bytes-long!!".getBytes());

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "accessTokenExpiry", 1_800_000L);
        // @ai_generated: 실제 운영값을 그대로 옮기지 않고 서로 다른 임의값을 넣어, createRefreshToken이
        // rememberMe 값에 따라 실제로 다른 필드를 참조하는지(분기 로직 자체)만 검증한다.
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshTokenExpiry", 1_800_000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshTokenExpiryRememberMe", 86_400_000L);
    }

    @Test
    void usrSn을_subject로_발급한_토큰은_정상적으로_추출된다() {
        String token = jwtTokenProvider.createAccessToken(101L);

        assertThat(jwtTokenProvider.getUsrSn(token)).isEqualTo(101L);
    }

    @Test
    void 로그인_유지를_체크하면_리프레시토큰_만료가_기본값보다_길게_발급된다() {
        String defaultToken = jwtTokenProvider.createRefreshToken(101L, false);
        String rememberMeToken = jwtTokenProvider.createRefreshToken(101L, true);

        Date defaultExpiry = expirationOf(defaultToken);
        Date rememberMeExpiry = expirationOf(rememberMeToken);

        assertThat(rememberMeExpiry).isAfter(defaultExpiry);
    }

    @Test
    void subject가_숫자형식이_아닌_구버전_토큰은_INVALID_TOKEN으로_변환한다() {
        String legacyToken = legacyTokenWithEmailSubject();

        assertThatThrownBy(() -> jwtTokenProvider.getUsrSn(legacyToken))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    private Date expirationOf(String token) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return claims.getExpiration();
    }

    /** 배포 이전(subject=email) 버전과 동일한 형태의 토큰을 같은 키로 서명해 재현한다. */
    private String legacyTokenWithEmailSubject() {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
        return Jwts.builder()
                   .subject("user@example.com")
                   .claim("role", "ROLE_USER")
                   .issuedAt(new Date())
                   .expiration(new Date(System.currentTimeMillis() + 1_800_000L))
                   .signWith(key)
                   .compact();
    }
}
