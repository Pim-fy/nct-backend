package nct.ops.reference.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 담당자 7 · F-AUC-013/F-OPS-003: 관리자 입찰 단위 등록·수정 입력값입니다. */
public record AdminBidUnitRequest(
        @NotNull
        @DecimalMin("1")
        @DecimalMax("999999999999999")
        @Digits(integer = 15, fraction = 0)
        BigDecimal amount,
        @NotBlank @Size(max = 500) String changeReason) {
}
