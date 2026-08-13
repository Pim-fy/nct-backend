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
            helper.setSubject("[에누리컷] 회원가입 이메일 인증번호");
            helper.setText("""
                    안녕하세요. 에누리컷 회원가입 이메일 인증번호입니다.

                    인증번호: %s
                    유효시간: 발송 후 3분

                    본인이 요청하지 않은 메일이라면 이 메일을 무시해 주세요.
                    """.formatted(code), false);
            javaMailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException ex) {
            throw new CustomException(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE);
        }
    }

    // @ai_generated: F-AUTH-007 - 비밀번호 재설정 링크 발송
    @Override
    public void sendPasswordResetLink(String email, String link) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());

            helper.setFrom(new InternetAddress(from, fromName));
            helper.setTo(email);
            helper.setSubject("[에누리컷] 비밀번호 재설정 안내");
            helper.setText("""
                    <p>안녕하세요. 에누리컷 비밀번호 재설정 링크입니다.</p>
                    <p>아래 링크를 클릭해 새 비밀번호를 설정해 주세요.</p>
                    <p><a href="%1$s">%1$s</a></p>
                    <p>유효시간: 발송 후 1시간 (1회 사용)</p>
                    <p>본인이 요청하지 않았다면 이 메일을 무시해 주세요.</p>
                    """.formatted(link), true);
            javaMailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException ex) {
            throw new CustomException(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE);
        }
    }

    // @ai_generated: F-AUTH-011 - 정지 계정용 탈퇴 확인 링크 발송(처음부터 HTML로 작성, plain text 재발 방지)
    @Override
    public void sendWithdrawalLink(String email, String link) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());

            helper.setFrom(new InternetAddress(from, fromName));
            helper.setTo(email);
            helper.setSubject("[에누리컷] 회원 탈퇴 확인 안내");
            helper.setText("""
                    <p>안녕하세요. 에누리컷 회원 탈퇴 확인 링크입니다.</p>
                    <p>본인이 요청하셨다면 아래 링크를 클릭해 탈퇴를 완료해 주세요.</p>
                    <p><a href="%1$s">%1$s</a></p>
                    <p>유효시간: 발송 후 1시간 (1회 사용)</p>
                    <p>본인이 요청하지 않았다면 이 메일을 무시해 주세요. 탈퇴는 이 링크를 클릭해야만 진행됩니다.</p>
                    """.formatted(link), true);
            javaMailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException ex) {
            throw new CustomException(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE);
        }
    }

    // @ai_generated: F-AUTH-017/POL-AUTH-016 - 정지 계정 문의 답변 통보(일방향, 회신 불가 안내 포함)
    @Override
    public void sendSuspendedInquiryAnswer(String email, String question, String answer) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());

            helper.setFrom(new InternetAddress(from, fromName));
            helper.setTo(email);
            helper.setSubject("[NCT] 문의하신 내용에 답변이 등록되었습니다");
            helper.setText("""
                    <p>안녕하세요. 문의하신 내용에 관리자 답변이 등록되었습니다.</p>
                    <p><b>문의 내용</b></p>
                    <p>%s</p>
                    <p><b>답변 내용</b></p>
                    <p>%s</p>
                    <p>이 메일은 발신 전용이라 회신하셔도 확인할 수 없습니다. 추가 문의가 있으시면
                    다시 로그인하시거나(정지 해제된 경우) 고객센터로 전화 문의해 주세요.</p>
                    """.formatted(question, answer), true);
            javaMailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException ex) {
            throw new CustomException(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE);
        }
    }
}
