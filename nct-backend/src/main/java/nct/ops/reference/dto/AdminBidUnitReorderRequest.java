package nct.ops.reference.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** 담당자 7 · 관리자 화면에서 전달하는 AUCG02 전체 표시 순서입니다. */
public record AdminBidUnitReorderRequest(
        @NotNull @NotEmpty List<@NotNull Long> bidUnitSnOrder) {
}
