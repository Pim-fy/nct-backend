package nct.ops.reference.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 담당자 7 · 입찰 단위 금액을 건드리지 않고 사용 상태만 변경하는 관리자 입력값입니다. */
public record AdminBidUnitStatusRequest(
        @NotNull Boolean active,
        @NotBlank @Size(max = 500) String changeReason) {
}
