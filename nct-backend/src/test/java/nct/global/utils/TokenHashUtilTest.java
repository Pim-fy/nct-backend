package nct.global.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// @ai_generated
/** Refresh Token 해시가 결정적이며(동일 입력 -> 동일 해시), 원문을 그대로 반환하지 않는지 검증한다. */
class TokenHashUtilTest {

    private final TokenHashUtil tokenHashUtil = new TokenHashUtil();

    @Test
    void 같은_토큰은_항상_같은_해시를_반환한다() {
        String hash1 = tokenHashUtil.hash("sample-refresh-token");
        String hash2 = tokenHashUtil.hash("sample-refresh-token");

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void 다른_토큰은_다른_해시를_반환한다() {
        String hash1 = tokenHashUtil.hash("token-a");
        String hash2 = tokenHashUtil.hash("token-b");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void 해시값은_원문_토큰과_다르다() {
        String rawToken = "sample-refresh-token";

        assertThat(tokenHashUtil.hash(rawToken)).isNotEqualTo(rawToken);
    }
}
