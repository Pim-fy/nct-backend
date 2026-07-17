package nct.ops.reference.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 담당자 7 · F-COM-003: 관리자 카테고리 등록·수정 입력값이다. */
public record AdminCategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Min(1) @Max(9999) Integer sortNo,
        @NotNull Boolean professional,
        @NotNull Boolean active,
        @NotBlank @Size(max = 500) String changeReason) {
}
