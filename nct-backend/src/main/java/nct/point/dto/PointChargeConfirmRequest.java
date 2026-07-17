package nct.point.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Claude Code 작성 (BJN, 2026-07-15)
 *
 * [포인트 충전 - 결제 승인 확정 요청]
 * - POST /api/point/charge/confirm 요청 본문
 * - amount는 일부러 받지 않는다 — 서버가 주문 생성 시 기록해 둔 금액만 신뢰 기준으로 쓴다(QSC-PG-01)
 */
@Getter
@Setter
public class PointChargeConfirmRequest {

    @NotBlank(message = "주문번호는 필수입니다.")
    private String orderId;

    @NotBlank(message = "결제키는 필수입니다.")
    private String paymentKey;
}
