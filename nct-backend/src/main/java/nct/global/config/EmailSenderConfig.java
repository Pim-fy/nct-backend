package nct.global.config;

import java.util.Properties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import nct.auth.service.EmailSender;
import nct.auth.service.SmtpEmailSender;
import nct.auth.service.UnavailableEmailSender;

// @ai_generated
/** Git 제외 로컬 메일 설정이 있을 때만 SMTP 발송기를 조립하고, 없으면 503 발송기로 유지한다. */
@Configuration(proxyBeanMethods = false)
@PropertySource(value = "file:./application-mail-local.properties", ignoreResourceNotFound = true)
public class EmailSenderConfig {

    @Bean
    public EmailSender emailSender(Environment environment) {
        if (!environment.getProperty("nct.mail.enabled", Boolean.class, false)) {
            return new UnavailableEmailSender();
        }

        String host = environment.getProperty("spring.mail.host");
        String username = environment.getProperty("spring.mail.username");
        String password = environment.getProperty("spring.mail.password");
        String from = environment.getProperty("nct.mail.from");
        if (!hasText(host) || !hasText(username) || !hasText(password) || !hasText(from)) {
            return new UnavailableEmailSender();
        }

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(environment.getProperty("spring.mail.port", Integer.class, 587));
        mailSender.setUsername(username);
        mailSender.setPassword(password);
        mailSender.setJavaMailProperties(javaMailProperties(environment));

        return new SmtpEmailSender(
                mailSender,
                from,
                environment.getProperty("nct.mail.from-name", "NCT"));
    }

    private Properties javaMailProperties(Environment environment) {
        Properties properties = new Properties();
        properties.put("mail.smtp.auth", environment.getProperty("spring.mail.properties.mail.smtp.auth", "true"));
        properties.put("mail.smtp.starttls.enable",
                environment.getProperty("spring.mail.properties.mail.smtp.starttls.enable", "true"));
        properties.put("mail.smtp.starttls.required",
                environment.getProperty("spring.mail.properties.mail.smtp.starttls.required", "true"));
        properties.put("mail.smtp.connectiontimeout",
                environment.getProperty("spring.mail.properties.mail.smtp.connectiontimeout", "5000"));
        properties.put("mail.smtp.timeout",
                environment.getProperty("spring.mail.properties.mail.smtp.timeout", "5000"));
        properties.put("mail.smtp.writetimeout",
                environment.getProperty("spring.mail.properties.mail.smtp.writetimeout", "5000"));
        return properties;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
