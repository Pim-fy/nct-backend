package nct.global.dto;

import java.util.List;

import com.github.pagehelper.PageInfo;

import lombok.Builder;
import lombok.Getter;

/**
 * [페이지네이션 공통 응답]
 * - PageHelper의 PageInfo를 직렬화 가능한 형태로 래핑
 */
@Getter
@Builder
public class PagedResponse<T> {

    private final List<T> list;
    private final int page;
    private final int size;
    private final long total;
    private final int totalPages;

    public static <T> PagedResponse<T> of(PageInfo<T> pageInfo) {
        return PagedResponse.<T>builder()
                .list(pageInfo.getList())
                .page(pageInfo.getPageNum())
                .size(pageInfo.getPageSize())
                .total(pageInfo.getTotal())
                .totalPages(pageInfo.getPages())
                .build();
    }
}
