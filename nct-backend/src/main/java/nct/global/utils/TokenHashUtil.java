package nct.global.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.springframework.stereotype.Component;

// @ai_generated: [Refresh Token 해시 유틸]
// - USERS.USR_REFRESH_TOKEN_HASH 는 컬럼명대로 해시만 저장한다 (DB 유출 시 원문 토큰 노출 방지)
// - 저장 전(MemberAuthAdapter.updateRefreshToken) / 대조 전(AuthService.verifyRefreshToken) 양쪽에서 동일하게 사용
@Component
public class TokenHashUtil {

    private static final String ALGORITHM = "SHA-256";

    /** Refresh Token 원문 -> SHA-256 해시(Base64) 문자열 */
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 JDK 표준 알고리즘이라 런타임에 발생하지 않음 - 방어적 변환만 수행
            throw new IllegalStateException("해시 알고리즘을 사용할 수 없습니다: " + ALGORITHM, e);
        }
    }
}
