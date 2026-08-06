package nct.ops.reference.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 담당자 7 · F-COM-003: 관리자 카테고리 등록·수정 입력값이다.
 * 표시 순서는 서버가 생성·순서 이동 시 관리하며, 기존 클라이언트의 sortNo는 호환용으로만 받는다.
 */
public record AdminCategoryRequest(
        @NotBlank @Size(max = 100) String name,
        Integer sortNo,
        @NotNull Boolean professional,
        @NotNull Boolean active,
        @Size(max = 500) String changeReason) {
}
