package nct.global.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

// @ai_generated
/** AES-GCM 비결정성·복호화·이메일 및 제공자 조회키 분리 규칙을 단위 검증한다. */
class FieldCryptoServiceTest {

    private FieldCryptoService fieldCryptoService;

    @BeforeEach
    void setUp() {
        CryptoProperties properties = new CryptoProperties();
        properties.setAesKeyBase64("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=");
        properties.setHmacKeyBase64("ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=");
        fieldCryptoService = new FieldCryptoService(properties);
        fieldCryptoService.initializeKeys();
    }

    @Test
    void 같은_평문도_매번_다른_AES_GCM_암호문으로_저장하고_복호화된다() {
        String first = fieldCryptoService.encrypt("user@example.com");
        String second = fieldCryptoService.encrypt("user@example.com");

        assertThat(first).startsWith("v1.").isNotEqualTo(second);
        assertThat(fieldCryptoService.decrypt(first)).isEqualTo("user@example.com");
        assertThat(fieldCryptoService.decrypt(second)).isEqualTo("user@example.com");
    }

    @Test
    void 이메일_HMAC은_trim_lowercase_정규화후_같아진다() {
        assertThat(fieldCryptoService.emailHmac(" User@Example.COM "))
                .isEqualTo(fieldCryptoService.emailHmac("user@example.com"));
    }

    @Test
    void 제공자코드가_다르면_같은_회원키도_다른_HMAC이_된다() {
        assertThat(fieldCryptoService.providerKeyHmac("kakao", "12345"))
                .isNotEqualTo(fieldCryptoService.providerKeyHmac("google", "12345"));
    }

    @Test
    void 위변조된_암호문은_복호화하지_않는다() {
        assertThatThrownBy(() -> fieldCryptoService.decrypt("v1.invalid.invalid"))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DATABASE_ERROR);
    }
}
