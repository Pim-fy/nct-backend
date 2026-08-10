package nct.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** F-OPS-009 관리자 정산 보류·해제 요청입니다. */
public record AdminSettlementActionRequest(
        @NotBlank @Size(max = 1000) String reason,
        @NotBlank @Size(max = 100) String requestId) {
}
