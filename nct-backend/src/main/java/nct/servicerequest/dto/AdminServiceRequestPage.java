package nct.servicerequest.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** 담당자 7: 관리자 서비스 요청 목록의 서버 페이지 응답이다. */
@Getter
@Builder
public class AdminServiceRequestPage {
    private final List<AdminServiceRequestListItem> items;
    private final int page;
    private final int size;
    private final long totalItems;
    private final int totalPages;
}
