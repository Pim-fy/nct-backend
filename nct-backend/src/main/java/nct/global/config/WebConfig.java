package nct.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import nct.global.idempotency.IdempotencyInterceptor;
import nct.global.logging.LogInterceptor;

import lombok.RequiredArgsConstructor;

/**
 * [설정 - Spring MVC 확장]
 * - LogInterceptor 등록 (로깅 3종 중 2번째 계층)
 * - /api/attachment/product/** 정적 리소스 서빙 (담당자6, F-AUC-002 이미지 연계)
 *   app.upload.dir/product 디스크 경로({yyyyMMdd}/파일명 구조)를 URL로 노출.
 * - IdempotencyInterceptor 등록 (전역 중복요청 방지, F-COM-017) // @ai_generated
 * - /api/attachment/** 정적 리소스 서빙 (담당자6, F-AUC-002 이미지 연계)
 *   app.upload.dir 디스크 경로({서비스}/{yyyyMMdd}/파일명 구조)를 URL로 노출.
 *   POST/DELETE/PUT /api/attachment 는 FileController(컨트롤러 매핑이 우선)가 담당하므로 충돌 없음.
 *   ⚠️ 공개 서빙은 product(상품 이미지)만 — provider(제공자 서류)는 민감정보라 정적 서빙에서
 *   물리적으로 제외하고, 관리자 전용 API(AdminProviderFileController)로만 열람한다 (2026-07-20)
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LogInterceptor logInterceptor;
    private final IdempotencyInterceptor idempotencyInterceptor; // @ai_generated

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(logInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/error",
                    "/favicon.ico"
                );
        // @ai_generated: 전역 중복요청 방지 - LogInterceptor 뒤에 등록
        registry.addInterceptor(idempotencyInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/error",
                    "/favicon.ico"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/attachment/product/**")
                .addResourceLocations("file:" + uploadDir + "/product/");
        // 리뷰 사진은 공개 서빙 — provider(민감 서류)와 달리 누구나 볼 수 있어야 한다
        registry.addResourceHandler("/api/attachment/review/**")
                .addResourceLocations("file:" + uploadDir + "/review/");
    }
}
