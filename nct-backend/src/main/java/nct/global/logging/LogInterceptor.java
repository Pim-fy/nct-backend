package nct.global.logging;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nct.ops.security.service.SensitiveDataMasker;

/**
 * [로깅 2계층 - 인터셉터]
 * - 컨트롤러 도달 요청의 상세 정보 로그
 *   : HTTP 메서드, URI, 클라이언트 IP, 로그인 사용자, 파라미터, 본문(JSON/폼/멀티파트)
 * - 본문은 LoggingFilter 가 캐싱해 둔 바이트 배열을 재사용
 * - 처리 시간/상태코드는 afterCompletion 에서 출력
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogInterceptor implements HandlerInterceptor {

    /** 로그에 남길 본문 최대 길이 */
    private static final int MAX_BODY_LEN = 1000;

    // F-OPS-012 공통 도구: 요청 본문·파라미터·URI·사용자 ID의 개인정보가 로그에 남는 것을 막는다.
    private final SensitiveDataMasker sensitiveDataMasker;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {

        // 정적 리소스 등 컨트롤러가 아닌 핸들러는 제외
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String contentType = request.getContentType();
        String queryParam  = extractQueryParam(request);

        StringBuilder sb = new StringBuilder();
        sb.append("\n======================= Access Log Start ==========================");
        sb.append("\nHTTP METHOD ::::    ").append(request.getMethod());
        sb.append("\nURI         ::::    ")
          .append(sensitiveDataMasker.maskUri(request.getRequestURI()));
        sb.append("\nCLIENT IP   ::::    ").append(MdcUtils.get(MdcUtils.CLIENT_IP));
        sb.append("\nTRACE ID    ::::    ").append(MdcUtils.get(MdcUtils.TRACE_ID));
        sb.append("\nUSER        ::::    ")
          .append(sensitiveDataMasker.maskText(extractUserId()));

        if (!queryParam.isEmpty()) {
            sb.append("\nPARAMETER   ::::    ").append(queryParam);
        }

        // JSON 본문은 여기서 출력하지 않음
        // : preHandle 시점에는 컨트롤러(@RequestBody)가 아직 본문을 읽지 않아
        //   ContentCachingRequestWrapper 캐시가 비어 있음 -> afterCompletion 에서 출력
        if (contentType != null) {
            if (contentType.contains(MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {
                sb.append("\nBODY        ::::    ").append(extractFormBody(request));
            } else if (contentType.contains(MediaType.MULTIPART_FORM_DATA_VALUE)) {
                sb.append("\nBODY        ::::    ").append(extractFormBody(request));
                sb.append("\nFILE COUNT  ::::    ").append(extractFileCount(request));
            }
        }

        sb.append("\n======================= Access Log End ============================");

        log.info(sb.toString());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {

        if (!(handler instanceof HandlerMethod)) {
            return;
        }

        Object startTime = request.getAttribute(LoggingFilter.ATTR_START_TIME);
        long elapsed = startTime instanceof Long start
                ? System.currentTimeMillis() - start
                : -1;

        // JSON 본문 출력 - 컨트롤러가 본문을 읽은 후라 캐시가 채워져 있음
        String contentType = request.getContentType();
        String bodyLog = "";
        if (contentType != null && contentType.contains(MediaType.APPLICATION_JSON_VALUE)) {
            bodyLog = "\nbody=" + extractJsonBody(request);
        }

        if (ex != null) {
            log.error("\n[응답] {} {} | status={} | {}ms{} | EXCEPTION={} : {}",
                      request.getMethod(), sensitiveDataMasker.maskUri(request.getRequestURI()),
                      response.getStatus(), elapsed, bodyLog,
                      ex.getClass().getSimpleName(), sensitiveDataMasker.maskText(ex.getMessage()));
        } else {
            log.info("\n[응답] {} {} | status={} | {}ms{}",
                     request.getMethod(), sensitiveDataMasker.maskUri(request.getRequestURI()),
                     response.getStatus(), elapsed, bodyLog);
        }
    }

    /** QueryString 파라미터 추출 (password/pw 포함 키는 마스킹) */
    private String extractQueryParam(HttpServletRequest request) {
        return Collections.list(request.getParameterNames())
                          .stream()
                          .map(name -> name + "=" + maskIfSensitive(name, request.getParameter(name)))
                          .collect(Collectors.joining(", "));
    }

    /**
     * application/json 본문 추출
     * - ContentCachingRequestWrapper 에서 직접 읽음
     *   (컨트롤러가 @RequestBody 로 본문을 읽은 뒤에만 캐시가 채워짐
     *    -> 반드시 afterCompletion 에서 호출할 것)
     * - "password" 류 필드 값은 마스킹
     */
    private String extractJsonBody(HttpServletRequest request) {
        if (!(request instanceof ContentCachingRequestWrapper wrapper)) {
            return "(no-cache)";
        }
        byte[] bytes = wrapper.getContentAsByteArray();
        if (bytes.length == 0) {
            return "(empty)";
        }
        String body = new String(bytes, StandardCharsets.UTF_8).trim();
        // JSON 내 비밀번호 필드 값 마스킹 (예: "password":"1234" -> "password":"***")
        body = body.replaceAll(
                "(\"[^\"]*(?:password|pw)[^\"]*\"\\s*:\\s*)\"[^\"]*\"",
                "$1\"***\"");
        body = sensitiveDataMasker.maskText(body);
        return body.length() > MAX_BODY_LEN
                ? body.substring(0, MAX_BODY_LEN) + "...(truncated)"
                : body;
    }

    /** 폼/멀티파트 텍스트 필드 추출 */
    private String extractFormBody(HttpServletRequest request) {
        String result = Collections.list(request.getParameterNames())
                                   .stream()
                                   .map(name -> name + "=" + maskIfSensitive(name, request.getParameter(name)))
                                   .collect(Collectors.joining(", "));
        return result.isEmpty() ? "(empty)" : result;
    }

    /** multipart 파일 개수 */
    private int extractFileCount(HttpServletRequest request) {
        if (request instanceof MultipartHttpServletRequest multipartRequest) {
            return multipartRequest.getFileMap().size();
        }
        return 0;
    }

    /** SecurityContext 에서 현재 로그인 사용자 추출 */
    private String extractUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return principal.toString();
    }

    /** password / pw 포함 파라미터 값 마스킹 */
    private String maskIfSensitive(String key, String value) {
        String lower = key.toLowerCase();
        return (lower.contains("password") || lower.contains("pw"))
                ? "***"
                : sensitiveDataMasker.maskText(value);
    }
}
