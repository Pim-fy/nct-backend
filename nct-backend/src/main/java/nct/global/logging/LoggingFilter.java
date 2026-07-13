package nct.global.logging;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * [로깅 1계층 - 서블릿 필터]
 * - 모든 요청의 최전선에서 실행 (@Order(1))
 * - 역할
 *   1) traceId 생성 -> MDC 주입 : 이후 모든 로그 라인에 같은 ID 가 찍혀 요청 추적 가능
 *   2) clientIp 추출 -> MDC 주입
 *   3) 요청 본문(body)을 캐싱 : InputStream 은 1회만 읽을 수 있으므로
 *      ContentCachingRequestWrapper 로 감싸 LogInterceptor 가 재사용할 수 있게 함
 */
@Slf4j
@Component
@Order(1)
public class LoggingFilter implements Filter {

    public static final String ATTR_START_TIME  = "startTime";
    public static final String ATTR_CACHED_BODY = "cachedBody";

    /** 캐싱할 본문 최대 크기 (100KB) */
    private static final int MAX_PAYLOAD_SIZE = 100 * 1024;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        ContentCachingRequestWrapper wrappedRequest =
                new ContentCachingRequestWrapper(httpRequest, MAX_PAYLOAD_SIZE);

        wrappedRequest.setAttribute(ATTR_START_TIME, System.currentTimeMillis());

        MdcUtils.put(MdcUtils.TRACE_ID,  MdcUtils.generateTraceId());
        MdcUtils.put(MdcUtils.CLIENT_IP, resolveClientIp(httpRequest));

        try {
            chain.doFilter(wrappedRequest, response);
        } finally {
            // chain 완료 후에야 body 캐시가 채워짐 -> 속성으로 보관
            wrappedRequest.setAttribute(ATTR_CACHED_BODY, wrappedRequest.getContentAsByteArray());
            // 스레드 풀 재사용 대비 MDC 정리 (누락 시 다른 요청 로그에 이전 traceId 가 섞임)
            MdcUtils.clear();
        }
    }

    /** 프록시/로드밸런서 환경 대응 클라이언트 IP 추출 */
    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return ip != null ? ip.split(",")[0].trim() : "-";
    }
}
