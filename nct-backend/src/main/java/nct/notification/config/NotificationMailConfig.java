package nct.notification.config;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import nct.notification.service.NotificationMailSender;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [알림 - 이메일 발송기 조립] (F-COM-006)
 * - 인증번호 발송기(EmailSenderConfig, 담당자1)와 같은 방식으로, git 미추적 로컬 메일 설정
 *   (application-mail-local.properties)이 있을 때만 실제 SMTP 발송기를 조립한다.
 *   설정이 없는 팀원 PC에서는 "발송 불가" 발송기가 들어가 알림이 전부 이메일 미대상으로 기록될 뿐,
 *   서비스는 정상 동작한다 (임의 기본값으로 몰래 되게 하지 않는다 — 없으면 안 보내는 게 정상).
 * - 담당자1의 설정 클래스를 수정하지 않고 별도 조립하는 이유: 타 담당자 소유 코드 미접촉 원칙
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@PropertySource(value = "file:./application-mail-local.properties", ignoreResourceNotFound = true)
public class NotificationMailConfig {

    @Bean
    public NotificationMailSender notificationMailSender(Environment env) {
        if (!env.getProperty("nct.mail.enabled", Boolean.class, false)) {
            return unavailable();
        }
        String host = env.getProperty("spring.mail.host");
        String username = env.getProperty("spring.mail.username");
        String password = env.getProperty("spring.mail.password");
        String from = env.getProperty("nct.mail.from");
        if (isBlank(host) || isBlank(username) || isBlank(password) || isBlank(from)) {
            return unavailable();
        }

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(env.getProperty("spring.mail.port", Integer.class, 587));
        mailSender.setUsername(username);
        mailSender.setPassword(password);
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        String fromName = env.getProperty("nct.mail.from-name", "에누리컷");
        return smtp(mailSender, from, fromName);
    }

    /** 실제 SMTP 발송기 — 어떤 실패도 예외 없이 false로 돌려준다 (베스트 에포트) */
    private NotificationMailSender smtp(JavaMailSenderImpl mailSender, String from, String fromName) {
        return new NotificationMailSender() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public boolean send(String toEmail, String subject, String body) {
                try {
                    MimeMessage message = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
                    helper.setFrom(new InternetAddress(from, fromName));
                    helper.setTo(toEmail);
                    helper.setSubject(subject);
                    helper.setText(body, false);
                    mailSender.send(message);
                    return true;
                } catch (Exception ex) {
                    // 이메일은 보조 채널 — 실패는 상태 기록용 false로만 알리고 본 처리를 막지 않는다
                    log.warn("[알림 이메일] 발송 실패 to={} subject={} cause={}", toEmail, subject, ex.getMessage());
                    return false;
                }
            }
        };
    }

    /** 메일 미설정 환경용 — 발송 불가를 정직하게 알린다 (알림은 이메일 미대상으로 기록됨) */
    private NotificationMailSender unavailable() {
        return new NotificationMailSender() {
            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public boolean send(String toEmail, String subject, String body) {
                return false;
            }
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
