package nct.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [감사 - 민감정보 제한 조회 요청 DTO] (F-OPS-014)
 * - POST /api/admin/audit/sensitive-view 요청 본문
 * - 세 값 전부 필수 — 분쟁 건 연결 없이, 사유 없이 원문을 여는 요청은 여기서부터 거절된다
 */
@Getter
@Setter
@NoArgsConstructor
public class SensitiveViewRequest {

    /** 조회 대상 채팅 메시지 일련번호 */
    @NotNull(message = "조회할 채팅 메시지 번호는 필수입니다.")
    private Long chMsgSn;

    /** 근거가 되는 거래 분쟁 건 일련번호 */
    @NotNull(message = "연결할 거래 분쟁 건 번호는 필수입니다.")
    private Long trdDspSn;

    /** 조회 사유 */
    @NotBlank(message = "조회 사유는 필수입니다.")
    private String reason;
}
