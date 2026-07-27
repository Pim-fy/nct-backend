package nct.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class OauthOnboardingRequestTest {

    @Test
    void 선택_전화번호는_숫자_열한자리만_허용한다() {
        // @ai_generated: 온보딩 요청도 로컬 가입과 동일한 전화번호 API 계약을 가진다.
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        OauthOnboardingRequest valid = new OauthOnboardingRequest();
        valid.setTelno("01012345678");
        OauthOnboardingRequest invalid = new OauthOnboardingRequest();
        invalid.setTelno("010-1234-5678");

        assertThat(validator.validate(valid)).noneMatch(v -> v.getPropertyPath().toString().equals("telno"));
        assertThat(validator.validate(invalid)).anyMatch(v -> v.getPropertyPath().toString().equals("telno"));
    }
}
