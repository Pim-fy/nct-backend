package nct.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * [설정 - 비밀번호 암호화]
 * - SecurityConfig 와 분리한 이유
 *   : SecurityConfig 가 PasswordEncoder 를 쓰는 서비스들을 주입받으면 순환 참조 위험
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
