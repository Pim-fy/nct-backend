package nct.trade.dto;

import java.util.List;

/** 서비스 거래 목록의 페이지 데이터와 탐색 메타데이터를 함께 반환한다. */
public record ServiceTradeListPageResponse(
        List<ServiceTradeListItem> content,
        int page,
        int size,
        long totalCount,
        int totalPages,
        boolean hasNext) {
}
