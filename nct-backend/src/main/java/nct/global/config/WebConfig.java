package nct.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import nct.global.logging.LogInterceptor;

import lombok.RequiredArgsConstructor;

/**
 * [설정 - Spring MVC 확장]
 * - LogInterceptor 등록 (로깅 3종 중 2번째 계층)
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LogInterceptor logInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(logInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/error",
                    "/favicon.ico"
                );
    }
}
