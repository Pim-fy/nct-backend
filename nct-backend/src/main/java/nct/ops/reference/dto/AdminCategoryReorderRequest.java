package nct.ops.reference.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 담당자 7 · F-COM-003: 드래그 후 도메인 전체의 카테고리 노출 순서를 저장하는 입력값이다. */
public record AdminCategoryReorderRequest(
        @NotEmpty @Valid List<@NotNull @Positive Long> categorySnOrder) {
}
