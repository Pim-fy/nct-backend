package nct.ops.operation.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** 담당자 7 · F-OPS-003: 관리자 경매 목록의 페이지 응답입니다. */
@Getter
@Builder
public class AdminAuctionPageResponse {
    private final List<AdminAuctionListItemResponse> items;
    private final int page;
    private final int size;
    private final long totalItems;
    private final int totalPages;
}
