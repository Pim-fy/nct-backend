package nct.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

// @ai_generated
/** SMTP 전달 어댑터가 인증번호만 포함한 메일을 구성하는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Test
    void 인증번호_메일을_발신자와_수신자_정보로_구성한다() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(message);
        SmtpEmailSender emailSender = new SmtpEmailSender(
                javaMailSender, "sender@example.com", "NCT");

        emailSender.sendVerificationCode("recipient@example.com", "123456");

        verify(javaMailSender).send(message);
        assertThat(message.getFrom()[0].toString()).contains("NCT").contains("sender@example.com");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("recipient@example.com");
        assertThat(message.getSubject()).isEqualTo("[NCT] 회원가입 이메일 인증번호");
        assertThat((String) message.getContent()).contains("123456").contains("3분");
    }

    @Test
    void 비밀번호_재설정_메일에_링크와_유효시간을_포함한다() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(message);
        SmtpEmailSender emailSender = new SmtpEmailSender(
                javaMailSender, "sender@example.com", "NCT");

        emailSender.sendPasswordResetLink("recipient@example.com",
                "http://localhost:5173/reset-password?token=abc123");

        verify(javaMailSender).send(message);
        assertThat(message.getSubject()).isEqualTo("[NCT] 비밀번호 재설정 안내");
        assertThat((String) message.getContent())
                .contains("http://localhost:5173/reset-password?token=abc123")
                .contains("1시간");
    }
}
