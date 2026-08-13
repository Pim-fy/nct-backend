package nct.ops.operation.dto;

/** 담당자 7 · F-OPS-003: 상품 숨김·복구 처리 결과입니다. */
public record AdminProductVisibilityResult(
        Long productId,
        boolean visible,
        boolean changed) {
}
