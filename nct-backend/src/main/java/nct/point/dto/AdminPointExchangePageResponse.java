package nct.point.dto;

import java.util.List;

import lombok.Builder;
import nct.global.response.PageResponse;
import nct.point.domain.PointExchangeOrder;

/** 담당자 7 · F-PAY-012: 관리자 환전 전체·상태별 목록의 페이징 결과입니다. */
@Builder
public record AdminPointExchangePageResponse(
        List<AdminPointExchangeOrderResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {

    public static AdminPointExchangePageResponse from(PageResponse<PointExchangeOrder> source) {
        long totalItems = source.getTotalCount();
        int totalPages = totalItems == 0
                ? 0
                : (int) ((totalItems + source.getSize() - 1) / source.getSize());
        return AdminPointExchangePageResponse.builder()
                .items(source.getContent().stream()
                        .map(AdminPointExchangeOrderResponse::from)
                        .toList())
                .page(source.getPage())
                .size(source.getSize())
                .totalItems(totalItems)
                .totalPages(totalPages)
                .build();
    }
}
