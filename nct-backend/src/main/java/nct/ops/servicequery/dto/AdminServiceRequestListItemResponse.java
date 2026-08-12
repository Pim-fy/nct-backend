package nct.ops.servicequery.dto;

import java.time.LocalDateTime;

import nct.quote.dto.AdminQuoteSummary;
import nct.point.dto.AdminEscrowSummary;
import nct.settlement.dto.AdminSettlementSummary;
import nct.servicerequest.dto.AdminServiceRequestListItem;
import nct.trade.dto.AdminServiceTradeSummary;
import nct.member.dto.AdminMemberIdentityResponse;

/** 담당자 7 · F-OPS-021: 원본 요청 상태와 조회용 통합 상태를 함께 반환하는 목록 행입니다. */
public record AdminServiceRequestListItemResponse(
        Long serviceRequestId,
        String title,
        Long categoryId,
        String categoryName,
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
        Long selectedQuoteId,
        Long tradeId,
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

    public static AdminServiceRequestListItemResponse from(
            AdminServiceRequestListItem source,
            AdminQuoteSummary quote,
            AdminServiceTradeSummary trade,
            AdminSettlementSummary settlement,
            AdminEscrowSummary escrow,
            AdminServiceRequestIntegratedStatus integratedStatus,
            AdminMemberIdentityResponse requesterMember) {
        return new AdminServiceRequestListItemResponse(
                source.getServiceRequestId(),
                source.getTitle(),
                source.getCategoryId(),
                source.getCategoryName(),
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
                quote == null ? null : quote.getSelectedQuoteId(),
                trade == null ? null : trade.getTradeId(),
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
