package nct.point.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import nct.global.exception.ErrorCode;
import nct.point.domain.PointChargeMethod;
import nct.point.exception.PointException;

/**
 * Claude Code 작성 (BJN, 2026-07-15)
 *
 * [포인트 - 토스페이먼츠 결제 승인 API 클라이언트] (F-PG-01)
 * - 시크릿 키로 서버 대 서버 통신만 한다. 프론트는 이 키를 절대 알지 못한다(클라이언트 키만 사용)
 * - 결제창/결제위젯은 서로 다른 키 쌍을 쓰므로 방식에 맞는 시크릿 키로 승인해야 한다
 * - 자체 재시도는 하지 않는다 — 재시도 정책(F-PG-04)은 호출하는 쪽에서 관리한다
 */
@Component
@RequiredArgsConstructor
public class TossPaymentsClient {

    private final ObjectMapper objectMapper;

    /** 결제창(개별 연동) 방식 시크릿 키 */
    @Value("${toss.payments.window.secret-key}")
    private String windowSecretKey;

    /** 결제위젯 방식 시크릿 키 */
    @Value("${toss.payments.widget.secret-key}")
    private String widgetSecretKey;

    @Value("${toss.payments.confirm-url}")
    private String confirmUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 결제 승인 확정.
     * amount는 항상 서버가 사전 기록해 둔 금액을 넘겨야 한다(호출부 책임) — 프론트가 보낸 금액을 신뢰하지 않는다.
     * method에 따라 해당 방식의 시크릿 키로 인증한다 — 다른 방식 키로 승인 요청하면 토스가 거절한다.
     */
    public TossConfirmResult confirm(String paymentKey, String orderId, long amount, PointChargeMethod method) {
        String secretKey = method == PointChargeMethod.WIDGET ? widgetSecretKey : windowSecretKey;
        String authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "paymentKey", paymentKey,
                    "orderId", orderId,
                    "amount", amount));
        } catch (Exception e) {
            throw new PointException(ErrorCode.EXTERNAL_API_ERROR, "결제 승인 요청 생성에 실패했습니다.");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(confirmUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            @SuppressWarnings("unchecked")
            Map<String, Object> json = objectMapper.readValue(response.body(), Map.class);

            if (response.statusCode() != 200) {
                Object messageValue = json.get("message");
                String message = messageValue != null ? String.valueOf(messageValue) : "결제 승인에 실패했습니다.";
                return TossConfirmResult.failure(message);
            }
            return TossConfirmResult.success(
                    (String) json.get("paymentKey"),
                    ((Number) json.get("totalAmount")).longValue(),
                    (String) json.get("status"));
        } catch (PointException e) {
            throw e;
        } catch (Exception e) {
            throw new PointException(ErrorCode.EXTERNAL_API_ERROR,
                    "토스페이먼츠 통신 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}
