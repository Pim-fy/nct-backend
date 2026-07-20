package nct.point.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Claude Code 작성 (BJN, 2026-07-15)
 *
 * [포인트 충전 - 주문 생성 응답]
 * - POST /api/point/charge/request 응답 본문
 * - 프론트는 이 값을 그대로 토스 결제창(SDK) 호출에 사용한다. clientKey는 공개 키라 노출돼도 안전하다
 */
@Getter
@Builder
public class PointChargeRequestResponse {

    private final String orderId;
    private final long amount;
    private final String orderName;
    private final String clientKey;

    public static PointChargeRequestResponse of(String orderId, long amount, String clientKey) {
        return PointChargeRequestResponse.builder()
                .orderId(orderId)
                .amount(amount)
                .orderName("포인트 충전 " + String.format("%,d", amount) + "P")
                .clientKey(clientKey)
                .build();
    }
}
