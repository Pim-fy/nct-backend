package nct.point.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Claude Code 작성 (BJN, 2026-07-17)
 *
 * [포인트 환전 - 관리자 반려 요청] (F-PAY-012)
 * - POST /api/admin/point/exchange/{번호}/reject 요청 본문
 * - 사유는 필수 — 신청자 알림과 신청 행 기록에 그대로 남아 분쟁 대응 근거가 된다
 */
@Getter
@Setter
public class AdminExchangeRejectRequest {

    @NotBlank(message = "반려 사유를 입력해 주세요.")
    private String reason;
}
