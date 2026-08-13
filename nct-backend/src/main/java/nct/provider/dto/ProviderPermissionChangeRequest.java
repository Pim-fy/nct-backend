package nct.provider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 담당자 7 · F-PROV-007: 제공자 카테고리 권한 정지·복구 요청값입니다. */
public record ProviderPermissionChangeRequest(
        @NotBlank @Size(max = 1000) String reason,
        @NotBlank @Size(max = 100) String requestId) {
}
