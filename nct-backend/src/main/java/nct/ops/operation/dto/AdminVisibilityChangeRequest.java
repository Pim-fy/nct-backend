package nct.ops.operation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 담당자 7: 관리자 노출 상태 변경의 목표값과 감사 사유를 함께 받습니다. */
public record AdminVisibilityChangeRequest(
        @NotNull Boolean visible,
        @NotBlank @Size(max = 1000) String reason,
        @NotBlank @Size(max = 100) String requestId) {
}
