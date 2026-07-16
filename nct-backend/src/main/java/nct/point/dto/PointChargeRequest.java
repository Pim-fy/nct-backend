package nct.point.dto;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Claude Code 작성 (BJN, 2026-07-15)
 *
 * [포인트 충전 - 주문 생성 요청]
 * - POST /api/point/charge/request 요청 본문
 */
@Getter
@Setter
public class PointChargeRequest {

    @Positive(message = "충전 금액은 0보다 커야 합니다.")
    private long amount;

    /** 충전 결제 방식 — "WINDOW"(결제창, 기본) 또는 "WIDGET"(결제위젯). 방식별 클라이언트 키가 다르다 */
    private String method;
}
