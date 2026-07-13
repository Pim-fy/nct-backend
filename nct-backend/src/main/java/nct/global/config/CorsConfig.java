package nct.global.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * [설정 - CORS]
 * - 허용 출처는 application.properties 의 cors.allowed-origins 로 관리
 * - allowCredentials(true) : httpOnly 쿠키(JWT) 전송을 위해 필수
 *   : 이 옵션이 true 면 allowedOrigins 에 "*" 사용 불가
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:http://localhost:5173}")
    private List<String> allowedOrigins;

    @Bean
    @Primary
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Accept", "X-Requested-With"));
        // 브라우저가 쿠키를 포함해 요청할 수 있도록 허용 (JWT httpOnly 쿠키 필수 설정)
        config.setAllowCredentials(true);
        // preflight(OPTIONS) 결과 캐시 시간
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
