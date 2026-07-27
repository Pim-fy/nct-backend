package nct.global.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * [공통 페이지네이션 응답]
 * - offset 기반(page/size) 페이지네이션의 표준 래퍼.
 * - content: 현재 페이지 데이터, totalCount: 전체 건수, hasNext: 다음 페이지 존재 여부.
 *
 * @param <T> 응답 원소 타입
 */
@Getter
@Builder
@AllArgsConstructor
public class PageResponse<T> {

    private final List<T> content;
    private final long totalCount;
    private final int page;
    private final int size;
    private final boolean hasNext;
}
