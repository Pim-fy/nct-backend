package nct.ops.notice.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * 공지 목록과 페이지 정보를 함께 반환한다.
 * 페이지 번호는 사용자 화면과 같은 1부터 시작하는 값이다.
 */
@Getter
@Builder
public class PublicNoticePageResponse {

    private final List<PublicNoticeListItemResponse> items;
    private final int page;
    private final int size;
    private final long totalItems;
    private final int totalPages;
}
