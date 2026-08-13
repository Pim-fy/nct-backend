package nct.ops.operation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 담당자 7 · F-OPS-003: 상품 공개 노출 전환과 필수 운영 사유입니다. */
public record AdminProductVisibilityRequest(
        @NotNull Boolean visible,
        @NotBlank @Size(max = 1000) String reason,
        @NotBlank @Size(max = 100) String requestId) {
}
