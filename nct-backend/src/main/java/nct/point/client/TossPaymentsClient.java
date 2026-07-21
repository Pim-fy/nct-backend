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
import nct.point.exception.PointException;

/**
 * Claude Code 작성 (BJN, 2026-07-15)
 *
 * [포인트 - 토스페이먼츠 결제 승인 API 클라이언트] (F-PG-01)
 * - 시크릿 키로 서버 대 서버 통신만 한다. 프론트는 이 키를 절대 알지 못한다(클라이언트 키만 사용)
 * - 충전 방식은 결제위젯 단일(사용자 결정, 2026-07-16)이라 위젯 전용 키(gsk)만 사용한다
 * - 자체 재시도는 하지 않는다 — 재시도 정책(F-PG-04)은 호출하는 쪽에서 관리한다
 */
@Component
@RequiredArgsConstructor
public class TossPaymentsClient {

    private final ObjectMapper objectMapper;

    /** 결제위젯 방식 시크릿 키 (gsk) — 프로젝트 루트 .env 파일에서 읽는다 */
    @Value("${toss.payments.widget.secret-key}")
    private String secretKey;

    @Value("${toss.payments.confirm-url}")
    private String confirmUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 결제 승인 확정.
     * amount는 항상 서버가 사전 기록해 둔 금액을 넘겨야 한다(호출부 책임) — 프론트가 보낸 금액을 신뢰하지 않는다.
     */
    public TossConfirmResult confirm(String paymentKey, String orderId, long amount) {
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
            return TossConfirmResult.success(((Number) json.get("totalAmount")).longValue());
        } catch (PointException e) {
            throw e;
        } catch (Exception e) {
            throw new PointException(ErrorCode.EXTERNAL_API_ERROR,
                    "토스페이먼츠 통신 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 결제 취소 (D-027 보상 전용 — PG 승인은 성공했는데 내부 반영이 실패했을 때 돈을 돌려준다).
     * 승인(confirm)과 달리 실패해도 예외를 던지지 않고 false를 돌려주는 이유:
     * 취소 실패는 "관리자 수동 확인" 경로(ⓑ)로 넘어가는 정상 분기라서, 호출부가
     * 성공/실패에 따라 실패 사유 문구만 다르게 기록하면 되기 때문.
     */
    public boolean cancel(String paymentKey, String cancelReason) {
        String authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

        try {
            String body = objectMapper.writeValueAsString(Map.of("cancelReason", cancelReason));

            // 취소 주소는 승인 주소와 같은 API 뿌리를 쓴다: .../v1/payments/{paymentKey}/cancel
            String cancelUrl = confirmUrl.replace("/confirm", "") + "/" + paymentKey + "/cancel";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cancelUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false; // 통신 오류도 "취소 실패"로 취급 — 관리자 확인 경로로
        }
    }
}
