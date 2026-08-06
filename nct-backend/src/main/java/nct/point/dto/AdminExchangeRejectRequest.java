package nct.point.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(max = 500, message = "반려 사유는 500자 이하여야 합니다.")
    private String reason;
}
