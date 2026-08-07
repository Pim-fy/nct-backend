package nct.ops.servicequery.dto;

import java.time.LocalDateTime;

import nct.quote.dto.AdminQuoteSummary;
import nct.servicerequest.dto.AdminServiceRequestDetail;

/** 담당자 7 · F-OPS-021: 서비스 요청 원본과 선택 견적 요약을 조립한 관리자 상세입니다. */
public record AdminServiceRequestDetailResponse(
        Long serviceRequestId,
        String title,
        String content,
        Long categoryId,
        String categoryName,
        Long formTemplateId,
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
        Long selectedProviderUserId,
        Long selectedAmount,
        String selectedQuoteStatusCode,
        LocalDateTime registeredAt,
        LocalDateTime updatedAt) {

    public static AdminServiceRequestDetailResponse from(
            AdminServiceRequestDetail source,
            AdminQuoteSummary quote,
            AdminServiceRequestIntegratedStatus integratedStatus) {
        return new AdminServiceRequestDetailResponse(
                source.getServiceRequestId(),
                source.getTitle(),
                source.getContent(),
                source.getCategoryId(),
                source.getCategoryName(),
                source.getFormTemplateId(),
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
                quote == null ? null : quote.getSelectedProviderUserId(),
                quote == null ? null : quote.getSelectedAmount(),
                quote == null ? null : quote.getSelectedQuoteStatusCode(),
                source.getRegisteredAt(),
                source.getUpdatedAt());
    }
}
