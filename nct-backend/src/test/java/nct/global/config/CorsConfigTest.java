package nct.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsConfigTest {

    /**
     * 개발자가 로그인 화면을 localhost 또는 127.0.0.1로 열어도
     * 브라우저가 JWT 쿠키 로그인 요청을 차단하지 않는지 확인한다.
     */
    @Test
    void allowsBothLocalFrontendOrigins() {
        CorsConfig corsConfig = new CorsConfig();
        ReflectionTestUtils.setField(
                corsConfig,
                "allowedOrigins",
                List.of("http://localhost:5173", "http://127.0.0.1:5173"));

        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest());

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("http://localhost:5173"))
                .isEqualTo("http://localhost:5173");
        assertThat(configuration.checkOrigin("http://127.0.0.1:5173"))
                .isEqualTo("http://127.0.0.1:5173");
        assertThat(configuration.checkOrigin("http://localhost:5174")).isNull();
        assertThat(configuration.checkOrigin("https://example.com")).isNull();
        assertThat(configuration.getAllowedOrigins()).doesNotContain("*");
        assertThat(configuration.getAllowCredentials()).isTrue();
    }
}
