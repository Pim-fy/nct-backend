package nct.point.dto;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [포인트 전환 - 신청 요청] (F-PAY-010)
 * - POST /api/point/convert 요청 본문
 * - 금액만 받는다 — 분쟁 여부·잔액 검증은 전부 서버가 한다 (프론트 검증 불신 원칙)
 */
@Getter
@Setter
public class PointConvertRequest {

    @Positive(message = "전환 금액은 0보다 커야 합니다.")
    private long amount;
}
