package nct.ops.operation.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** 담당자 7 · F-OPS-005: 관리자 거래 분쟁 페이지 응답입니다. */
@Getter
@Builder
public class AdminDisputePageResponse {

    private final List<AdminDisputeListItemResponse> items;
    private final int page;
    private final int size;
    private final long totalItems;
    private final int totalPages;
}
