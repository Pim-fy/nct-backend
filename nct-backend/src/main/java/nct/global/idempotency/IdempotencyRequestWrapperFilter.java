package nct.global.idempotency;

import java.io.IOException;
import java.util.Set;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// @ai_generated: [전역 중복요청 방지 - 요청/응답 캐싱 필터] (F-COM-017)
// - LoggingFilter(@Order(1))의 ContentCachingRequestWrapper는 체인 완료 후에만 바디가 채워져
//   IdempotencyInterceptor(preHandle, 컨트롤러 실행 전)에서는 재사용할 수 없다. 그래서 자체
//   반복읽기 래퍼(CachedBodyHttpServletRequest)로 별도 감싼다.
// - 멀티파트(파일 업로드/교체)는 바디 전체를 메모리에 올리는 비용이 커서 애초에 캐싱 대상에서
//   제외한다 - 어차피 @SkipIdempotency 대상이라 인터셉터가 바디를 읽지 않지만, 여기서 걸러야
//   불필요한 버퍼링 자체가 발생하지 않는다.
@Component
@Order(2)
public class IdempotencyRequestWrapperFilter implements Filter {

    private static final Set<String> TARGET_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        if (!TARGET_METHODS.contains(httpRequest.getMethod()) || isMultipart(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(httpRequest);
        ContentCachingResponseWrapper wrappedResponse =
                new ContentCachingResponseWrapper((HttpServletResponse) response);

        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            wrappedResponse.copyBodyToResponse();
        }
    }

    private boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }
}
