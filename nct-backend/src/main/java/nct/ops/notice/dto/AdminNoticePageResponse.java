package nct.ops.notice.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** 관리자 공지 목록과 1부터 시작하는 페이지 정보를 함께 반환한다. */
@Getter
@Builder
public class AdminNoticePageResponse {

    private final List<AdminNoticeListItemResponse> items;
    private final int page;
    private final int size;
    private final long totalItems;
    private final int totalPages;
}
