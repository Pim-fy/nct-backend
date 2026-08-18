package nct.ops.security.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveDataMaskerTest {

    private final SensitiveDataMasker masker = new SensitiveDataMasker();

    @Test
    void masksEmailPhoneAndAccountNumber() {
        String source = "메일 test@example.com 전화 010-1234-5678 계좌 123-456-789012";

        MaskingResult result = masker.mask(source);

        assertThat(result.maskedText())
                .doesNotContain("test@example.com", "010-1234-5678", "123-456-789012")
                .contains("[이메일 마스킹]", "[연락처 마스킹]", "[계좌번호 마스킹]");
        assertThat(result.detectedTypes()).containsExactlyInAnyOrder(
                SensitiveDataType.EMAIL,
                SensitiveDataType.PHONE_NUMBER,
                SensitiveDataType.ACCOUNT_NUMBER);
    }

    @Test
    void keepsOrdinaryDateAndShortNumber() {
        String source = "처리일 2026-07-15, 주문번호 12345";

        MaskingResult result = masker.mask(source);

        assertThat(result.maskedText()).isEqualTo(source);
        assertThat(result.detected()).isFalse();
    }

    /** 담당자 7 · ISSUE-T7-013: 해시·영문 식별자·포인트 금액은 개인정보로 오인하지 않습니다. */
    @Test
    void keepsHashIdentifierAndPointAmount() {
        String hash = "01012345678" + "a".repeat(53);
        String source = "request=" + hash
                + ", 식별자 ABC123456789012XYZ, 전체 합계 15881234P";

        MaskingResult result = masker.mask(source);

        assertThat(result.maskedText()).isEqualTo(source);
        assertThat(result.detected()).isFalse();
    }

    @Test
    void handlesNullAndEmptyText() {
        assertThat(masker.mask(null).maskedText()).isNull();
        assertThat(masker.mask("").maskedText()).isEmpty();
    }

    @Test
    void masksInternetAndRepresentativePhoneNumbers() {
        MaskingResult result = masker.mask("070-1234-5678 / 1588-1234 / 0505-123-4567");

        assertThat(result.maskedText())
                .doesNotContain("070-1234-5678", "1588-1234", "0505-123-4567");
        assertThat(result.detectedTypes()).contains(SensitiveDataType.PHONE_NUMBER);
    }

    @Test
    void decodesUriBeforeMasking() {
        assertThat(masker.maskUri("/users/user%40example.com"))
                .doesNotContain("user@example.com", "user%40example.com");
    }
}
