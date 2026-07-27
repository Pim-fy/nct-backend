package nct.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import nct.auth.service.SmtpEmailSender;
import nct.auth.service.UnavailableEmailSender;

// @ai_generated
/** 로컬 메일 설정의 유무가 발송기 선택에만 영향을 주는지 검증한다. */
class EmailSenderConfigTest {

    private final EmailSenderConfig emailSenderConfig = new EmailSenderConfig();

    @Test
    void 메일_활성_설정이_없으면_발송_불가_구현체를_사용한다() {
        assertThat(emailSenderConfig.emailSender(new MockEnvironment()))
                .isInstanceOf(UnavailableEmailSender.class);
    }

    @Test
    void 필수_SMTP_설정이_있으면_SMTP_구현체를_사용한다() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("nct.mail.enabled", "true")
                .withProperty("spring.mail.host", "smtp.gmail.com")
                .withProperty("spring.mail.username", "sender@example.com")
                .withProperty("spring.mail.password", "app-password")
                .withProperty("nct.mail.from", "sender@example.com");

        assertThat(emailSenderConfig.emailSender(environment))
                .isInstanceOf(SmtpEmailSender.class);
    }
}
