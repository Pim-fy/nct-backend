package nct.ops.risk.dto;

import java.util.List;

import lombok.Builder;

/** 담당자 7 · F-OPS-011: 목록 화면의 페이징 결과입니다. */
@Builder
public record AdminRiskEventPageResponse(List<AdminRiskEventListItemResponse> items,
                                         int page, int size, long totalItems, int totalPages) {
}
