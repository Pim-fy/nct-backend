package nct.ops.operation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 담당자 7 - F-OPS-007: 7일 신고 제재를 관리자가 조기 해제할 때 쓰는 사유입니다. */
public record AdminReportSanctionReleaseRequest(
        @NotBlank @Size(max = 1000) String reason) {
}
