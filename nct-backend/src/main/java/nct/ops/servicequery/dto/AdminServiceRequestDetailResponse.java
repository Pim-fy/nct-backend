package nct.ops.servicequery.dto;

import java.time.LocalDateTime;
import java.util.List;

import nct.quote.dto.AdminQuoteSummary;
import nct.point.dto.AdminEscrowSummary;
import nct.settlement.dto.AdminSettlementSummary;
import nct.servicerequest.dto.AdminServiceRequestDetail;
import nct.trade.dto.AdminServiceTradeSummary;
import nct.member.dto.AdminMemberIdentityResponse;

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
        AdminMemberIdentityResponse requesterMember,
        Long budgetAmount,
        String statusCode,
        String statusName,
        boolean visible,
        String integratedStatusCode,
        String integratedStatusName,
        int totalQuoteCount,
        int activeQuoteCount,
        List<AdminServiceRequestQuoteResponse> quotes,
        Long selectedQuoteId,
        Long selectedProviderUserId,
        AdminMemberIdentityResponse selectedProviderMember,
        Long selectedAmount,
        String selectedQuoteStatusCode,
        Long tradeId,
        Long tradeQuoteId,
        String tradeStatusCode,
        String tradeStatusName,
        Long activeDisputeId,
        String activeDisputeStatusCode,
        String activeDisputeStatusName,
        Long settlementId,
        String settlementStatusCode,
        String settlementStatusName,
        long activeEscrowAmount,
        long refundedPointAmount,
        long settledPointAmount,
        LocalDateTime registeredAt,
        LocalDateTime updatedAt) {

    public static AdminServiceRequestDetailResponse from(
            AdminServiceRequestDetail source,
            AdminQuoteSummary quote,
            AdminServiceTradeSummary trade,
            AdminSettlementSummary settlement,
            AdminEscrowSummary escrow,
            AdminServiceRequestIntegratedStatus integratedStatus,
            AdminMemberIdentityResponse requesterMember,
            AdminMemberIdentityResponse selectedProviderMember,
            List<AdminServiceRequestQuoteResponse> quotes) {
        return new AdminServiceRequestDetailResponse(
                source.getServiceRequestId(),
                source.getTitle(),
                source.getContent(),
                source.getCategoryId(),
                source.getCategoryName(),
                source.getFormTemplateId(),
                source.getRequesterUserId(),
                source.getRequesterName(),
                requesterMember,
                source.getBudgetAmount(),
                source.getStatusCode(),
                source.getStatusName(),
                "Y".equals(source.getUseYn()),
                integratedStatus.code(),
                integratedStatus.label(),
                quote == null ? 0 : quote.getTotalQuoteCount(),
                quote == null ? 0 : quote.getActiveQuoteCount(),
                List.copyOf(quotes),
                quote == null ? null : quote.getSelectedQuoteId(),
                quote == null ? null : quote.getSelectedProviderUserId(),
                selectedProviderMember,
                quote == null ? null : quote.getSelectedAmount(),
                quote == null ? null : quote.getSelectedQuoteStatusCode(),
                trade == null ? null : trade.getTradeId(),
                trade == null ? null : trade.getQuoteId(),
                trade == null ? null : trade.getTradeStatusCode(),
                trade == null ? null : trade.getTradeStatusName(),
                trade == null ? null : trade.getActiveDisputeId(),
                trade == null ? null : trade.getActiveDisputeStatusCode(),
                trade == null ? null : trade.getActiveDisputeStatusName(),
                settlement == null ? null : settlement.getSettlementId(),
                settlement == null ? null : settlement.getStatusCode(),
                settlement == null ? null : settlement.getStatusName(),
                escrow == null ? 0 : escrow.activeEscrowAmount(),
                escrow == null ? 0 : escrow.getRefundedAmount(),
                escrow == null ? 0 : escrow.getSettledAmount(),
                source.getRegisteredAt(),
                source.getUpdatedAt());
    }
}
