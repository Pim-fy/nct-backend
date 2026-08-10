package nct.settlement.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** F-OPS-009 관리자 정산 페이지 응답입니다. */
@Getter
@Builder
public class AdminSettlementPageResponse {

    private final List<AdminSettlementListItemResponse> items;
    private final int page;
    private final int size;
    private final long totalItems;
    private final int totalPages;
}
