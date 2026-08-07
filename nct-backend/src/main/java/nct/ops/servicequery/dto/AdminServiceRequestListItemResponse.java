package nct.ops.servicequery.dto;

import java.time.LocalDateTime;

import nct.quote.dto.AdminQuoteSummary;
import nct.servicerequest.dto.AdminServiceRequestListItem;

/** 담당자 7 · F-OPS-021: 원본 요청 상태와 조회용 통합 상태를 함께 반환하는 목록 행입니다. */
public record AdminServiceRequestListItemResponse(
        Long serviceRequestId,
        String title,
        Long categoryId,
        String categoryName,
        Long requesterUserId,
        String requesterName,
        Long budgetAmount,
        String statusCode,
        String statusName,
        String integratedStatusCode,
        String integratedStatusName,
        int totalQuoteCount,
        int activeQuoteCount,
        Long selectedQuoteId,
        LocalDateTime registeredAt,
        LocalDateTime updatedAt) {

    public static AdminServiceRequestListItemResponse from(
            AdminServiceRequestListItem source,
            AdminQuoteSummary quote,
            AdminServiceRequestIntegratedStatus integratedStatus) {
        return new AdminServiceRequestListItemResponse(
                source.getServiceRequestId(),
                source.getTitle(),
                source.getCategoryId(),
                source.getCategoryName(),
                source.getRequesterUserId(),
                source.getRequesterName(),
                source.getBudgetAmount(),
                source.getStatusCode(),
                source.getStatusName(),
                integratedStatus.code(),
                integratedStatus.label(),
                quote == null ? 0 : quote.getTotalQuoteCount(),
                quote == null ? 0 : quote.getActiveQuoteCount(),
                quote == null ? null : quote.getSelectedQuoteId(),
                source.getRegisteredAt(),
                source.getUpdatedAt());
    }
}
