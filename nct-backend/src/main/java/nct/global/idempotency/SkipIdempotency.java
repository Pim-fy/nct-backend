package nct.global.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// @ai_generated: [전역 중복요청 방지 opt-out 마커] (F-COM-017)
// - IdempotencyInterceptor가 이 애노테이션이 붙은 메서드/클래스는 지문 계산·중복 판정을 건너뛴다
// - 대상: Set-Cookie 응답을 내려주는 엔드포인트(login, refresh),
//   멀티파트 바디라 캐싱 대상에서 제외되는 파일 업로드/교체
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SkipIdempotency {
}
