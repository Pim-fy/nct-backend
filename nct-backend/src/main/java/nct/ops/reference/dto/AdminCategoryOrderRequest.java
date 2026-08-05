package nct.ops.reference.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 담당자 7 · F-COM-003: 관리자 목록에서 카테고리를 한 칸 위·아래로 이동하는 입력값이다. */
public record AdminCategoryOrderRequest(
        @NotBlank @Pattern(regexp = "UP|DOWN") String direction) {
}
