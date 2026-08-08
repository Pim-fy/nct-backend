package nct.ops.servicequery.dto;

import java.util.List;

/** 담당자 7 · F-OPS-021: 통합상태가 포함된 관리자 견적 요청 페이지 응답입니다. */
public record AdminServiceRequestPageResponse(
        List<AdminServiceRequestListItemResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}
