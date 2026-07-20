package nct.point.dto;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Claude Code 작성 (BJN, 2026-07-17)
 *
 * [포인트 환전 - 신청 요청] (F-PAY-012)
 * - POST /api/point/exchange 요청 본문
 * - 계좌 정보는 받지 않는다 — 서버가 USERS의 등록 계좌를 읽어 스냅샷으로 기록한다
 *   (프론트가 보낸 계좌를 믿으면 남의 계좌로 지급 요청이 가능해지므로)
 */
@Getter
@Setter
public class PointExchangeRequest {

    @Positive(message = "환전 금액은 0보다 커야 합니다.")
    private long amount;
}
