package nct.auth.service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

// @ai_generated
/** SMTP가 활성화된 로컬 환경에서만 가입 인증번호를 실제 이메일로 전달한다. */
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;
    private final String from;
    private final String fromName;

    public SmtpEmailSender(JavaMailSender javaMailSender, String from, String fromName) {
        this.javaMailSender = javaMailSender;
        this.from = from;
        this.fromName = fromName;
    }

    @Override
    public void sendVerificationCode(String email, String code) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());

            helper.setFrom(new InternetAddress(from, fromName));
            helper.setTo(email);
            helper.setSubject("[NCT] 회원가입 이메일 인증번호");
            helper.setText("""
                    안녕하세요. NCT 회원가입 이메일 인증번호입니다.

                    인증번호: %s
                    유효시간: 발송 후 3분

                    본인이 요청하지 않은 메일이라면 이 메일을 무시해 주세요.
                    """.formatted(code), false);
            javaMailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException ex) {
            throw new CustomException(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE);
        }
    }
}
