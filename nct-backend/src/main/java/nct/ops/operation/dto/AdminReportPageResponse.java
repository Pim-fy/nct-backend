package nct.ops.operation.dto;

import java.util.List;

import lombok.Builder;
import nct.abuse.dto.AdminAbuseReportResponse;

/** 담당자 7 · F-OPS-007: 관리자 신고 전체·상태별 목록의 페이징 결과입니다. */
@Builder
public record AdminReportPageResponse(
        List<AdminAbuseReportResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}
