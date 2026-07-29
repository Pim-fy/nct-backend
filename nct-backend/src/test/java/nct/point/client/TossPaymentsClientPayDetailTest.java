package nct.point.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Claude Code 작성 (BJN, 2026-07-29)
 *
 * [포인트 충전 - 토스 응답 결제수단 상세 파싱] 사용자가 충전 내역에 결제수단(카드/간편결제)을
 * 보여주고 싶어해서 추가한 TossPaymentsClient.extractPayDetail 파싱 로직 검증.
 * 네트워크 호출 없이 순수 파싱 로직만 확인 — HttpClient를 안 쓰는 메서드라 목킹 불필요.
 */
class TossPaymentsClientPayDetailTest {

    private final TossPaymentsClient client = new TossPaymentsClient(new ObjectMapper());

    @Test
    void extractsMaskedCardNumberWhenMethodIsCard() {
        Map<String, Object> json = Map.of("method", "카드", "card", Map.of("number", "433012******1234"));

        assertThat(client.extractPayDetail(json, "카드")).isEqualTo("433012******1234");
    }

    @Test
    void extractsEasyPayProviderWhenMethodIsEasyPay() {
        Map<String, Object> json = Map.of("method", "간편결제", "easyPay", Map.of("provider", "토스페이"));

        assertThat(client.extractPayDetail(json, "간편결제")).isEqualTo("토스페이");
    }

    @Test
    void returnsNullForMethodsWithoutDetail() {
        Map<String, Object> json = Map.of("method", "계좌이체");

        assertThat(client.extractPayDetail(json, "계좌이체")).isNull();
    }

    @Test
    void returnsNullWhenCardMethodButNoCardObject() {
        Map<String, Object> json = Map.of("method", "카드");

        assertThat(client.extractPayDetail(json, "카드")).isNull();
    }
}
