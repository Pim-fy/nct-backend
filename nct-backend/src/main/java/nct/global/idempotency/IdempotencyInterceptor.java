package nct.global.idempotency;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.fasterxml.jackson.databind.ObjectMapper;

import nct.global.logging.MdcUtils;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.global.utils.CookieUtil;
import nct.global.utils.TokenHashUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

// @ai_generated: [전역 중복요청 방지(멱등성) 인터셉터] (F-COM-017)
// - WebConfig에 LogInterceptor 뒤에 등록된다.
// - 지문(식별자+Method+URI+BodyHash)을 REQ_FGPT에 INSERT 시도해 신규/중복을 판정한다.
//   INSERT 성공: 신규 요청 -> 정상 처리 진행, afterCompletion에서 응답을 저장한다.
//   UNIQUE 충돌: 기존 행 조회 -> 완료된 응답 있으면 그대로 재반환, 아직 처리 중이면 409.
// - GlobalExceptionHandler를 거치지 않고 이 클래스가 직접 응답을 쓴다. 원본 응답(임의의 상태코드
//   ·바디)을 정확히 재현해야 하는데, GlobalExceptionHandler는 고정된 에러 포맷만 반환하기 때문이다
//   (SPEC.md 5번 판단 근거 참고).
@Component
@RequiredArgsConstructor
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final Set<String> TARGET_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final long TTL_SECONDS = 5;
    private static final String ATTR_HASH = "idempotency.hash";

    private final RequestFingerprintMapper fingerprintMapper;
    private final CookieUtil cookieUtil;
    private final TokenHashUtil tokenHashUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        if (!TARGET_METHODS.contains(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod) || isSkip(handlerMethod)) {
            return true;
        }
        // @ai_generated: QA(P3) 반영 - 필터가 바디를 캐싱하지 않은 요청(예: 향후 추가될 @SkipIdempotency
        // 없는 멀티파트 엔드포인트)은 빈 바디로 취급해 서로 다른 요청이 같은 지문으로 오판되지 않도록,
        // 바디 미캐싱 상태에서는 보호 자체를 건너뛴다.
        if (!(request instanceof CachedBodyHttpServletRequest)) {
            return true;
        }

        String identifier = resolveIdentifier(request);
        String bodyHash = hashBody(request);
        String fingerprint = sha256Hex(
                identifier + "|" + request.getMethod() + "|" + request.getRequestURI() + "|" + bodyHash);

        try {
            fingerprintMapper.tryInsert(fingerprint, LocalDateTime.now().plusSeconds(TTL_SECONDS));
            request.setAttribute(ATTR_HASH, fingerprint);
            return true;
        } catch (DuplicateKeyException e) {
            Map<String, Object> existing = fingerprintMapper.selectByHash(fingerprint);
            if (existing != null && existing.get("RQF_RESP_STAT") != null) {
                replayResponse(response, existing);
            } else {
                writeProcessingResponse(request, response);
            }
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {

        Object hashAttr = request.getAttribute(ATTR_HASH);
        if (hashAttr == null) {
            return;
        }
        String fingerprint = (String) hashAttr;
        int status = response.getStatus();
        String body = null;
        if (response instanceof ContentCachingResponseWrapper wrapper) {
            byte[] content = wrapper.getContentAsByteArray();
            if (content.length > 0) {
                Charset charset = wrapper.getCharacterEncoding() != null
                        ? Charset.forName(wrapper.getCharacterEncoding())
                        : StandardCharsets.UTF_8;
                body = new String(content, charset);
            }
        }
        fingerprintMapper.updateResponse(fingerprint, status, body);
    }

    private boolean isSkip(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(SkipIdempotency.class)
                || handlerMethod.getBeanType().isAnnotationPresent(SkipIdempotency.class);
    }

    /** 식별자 우선순위: 인증 사용자ID > 인증 쿠키/토큰값(Refresh-Token) > IP */
    private String resolveIdentifier(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return "user:" + userDetails.getMember().getId();
        }
        String refreshToken = cookieUtil.extractCookie(request, CookieUtil.REFRESH_TOKEN_COOKIE);
        if (refreshToken != null && !refreshToken.isBlank()) {
            return "cookie:" + tokenHashUtil.hash(refreshToken);
        }
        return "ip:" + MdcUtils.get(MdcUtils.CLIENT_IP);
    }

    private String hashBody(HttpServletRequest request) {
        byte[] body = request instanceof CachedBodyHttpServletRequest cached
                ? cached.getCachedBody()
                : new byte[0];
        return sha256Hex(body);
    }

    private void replayResponse(HttpServletResponse response, Map<String, Object> existing)
            throws java.io.IOException {
        int status = ((Number) existing.get("RQF_RESP_STAT")).intValue();
        String body = (String) existing.get("RQF_RESP_BODY");
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        if (body != null) {
            response.getWriter().write(body);
        }
    }

    private void writeProcessingResponse(HttpServletRequest request, HttpServletResponse response)
            throws java.io.IOException {
        response.setStatus(409);
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> body = ApiResponse.error(
                409, "처리 중인 요청입니다. 잠시 후 다시 시도하세요", request.getRequestURI(), "IDEMPOTENCY_PROCESSING");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private String sha256Hex(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("해시 알고리즘을 사용할 수 없습니다: SHA-256", e);
        }
    }
}
