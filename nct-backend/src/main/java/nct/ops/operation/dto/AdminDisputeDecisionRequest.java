package nct.ops.operation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import nct.ops.operation.domain.AdminDisputeDecision;

/** 담당자 7 · F-OPS-006: 관리자 분쟁 판정 입력입니다. 부분 금액은 받지 않습니다. */
public record AdminDisputeDecisionRequest(
        @NotNull AdminDisputeDecision decision,
        @NotBlank @Size(max = 1000) String reason) {
}
