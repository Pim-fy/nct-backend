package nct.ops.servicequery.service;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.member.dto.AdminMemberIdentityResponse;
import nct.member.port.AdminMemberIdentityReader;
import nct.ops.reference.service.AdminCategoryService;
import nct.ops.servicequery.dto.AdminServiceRequestListRequest;
import nct.ops.servicequery.dto.AdminServiceRequestDetailResponse;
import nct.ops.servicequery.dto.AdminServiceRequestIntegratedStatus;
import nct.ops.servicequery.dto.AdminServiceRequestListItemResponse;
import nct.ops.servicequery.dto.AdminServiceRequestPageResponse;
import nct.ops.security.service.SensitiveDataMasker;
import nct.point.dto.AdminEscrowSummary;
import nct.point.port.AdminEscrowSummaryReader;
import nct.quote.dto.AdminQuoteSummary;
import nct.quote.port.AdminQuoteSummaryReader;
import nct.settlement.dto.AdminSettlementSummary;
import nct.settlement.port.AdminSettlementSummaryReader;
import nct.servicerequest.dto.AdminServiceRequestDetail;
import nct.servicerequest.dto.AdminServiceRequestPage;
import nct.servicerequest.dto.AdminServiceRequestSearchCondition;
import nct.servicerequest.port.AdminServiceRequestReader;
import nct.trade.dto.AdminServiceTradeSummary;
import nct.trade.port.AdminServiceTradeSummaryReader;

/**
 * 담당자 7 · 관리자 42 서비스 요청 관리.
 * 관리자 검색값을 검증한 뒤 서비스요청 도메인의 읽기 계약만 호출한다.
 */
@Service
@RequiredArgsConstructor
public class AdminServiceRequestQueryService {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final String SERVICE_CATEGORY_DOMAIN = "CATC0002";

    private final AdminServiceRequestReader reader;
    private final SensitiveDataMasker sensitiveDataMasker;
    private final AdminCategoryService adminCategoryService;
    private final AdminQuoteSummaryReader quoteSummaryReader;
    private final AdminServiceTradeSummaryReader tradeSummaryReader;
    private final AdminSettlementSummaryReader settlementSummaryReader;
    private final AdminEscrowSummaryReader escrowSummaryReader;
    private final AdminMemberIdentityReader memberIdentityReader;

    @Transactional(readOnly = true)
    public AdminServiceRequestPageResponse getPage(AdminServiceRequestListRequest request) {
        AdminServiceRequestListRequest normalized = request == null
                ? new AdminServiceRequestListRequest()
                : request;
        normalize(normalized);
        AdminServiceRequestPage page = reader.readPage(AdminServiceRequestSearchCondition.builder()
                .keyword(normalized.getKeyword())
                .categorySn(normalized.getCategorySn())
                .statusCode(normalized.getStatusCode())
                .registeredFrom(normalized.getRegisteredFrom())
                .registeredTo(normalized.getRegisteredTo())
                .page(normalized.getPage())
                .size(normalized.getSize())
                .build());
        page.getItems().forEach(item -> item.setTitle(sensitiveDataMasker.maskText(item.getTitle())));
        List<Long> serviceRequestIds = page.getItems().stream()
                .map(item -> item.getServiceRequestId())
                .distinct()
                .toList();
        Map<Long, AdminQuoteSummary> quoteSummaries = quoteSummaryReader.findSummaries(serviceRequestIds);
        if (!serviceRequestIds.containsAll(quoteSummaries.keySet())) {
            throw inconsistent("조회 대상이 아닌 견적 요약이 반환되었습니다.");
        }
        ServiceFlowSummaries flow = loadFlowSummaries(serviceRequestIds);
        Set<Long> memberIds = new LinkedHashSet<>();
        page.getItems().forEach(item -> memberIds.add(item.getRequesterUserId()));
        quoteSummaries.values().stream()
                .map(AdminQuoteSummary::getSelectedProviderUserId)
                .filter(java.util.Objects::nonNull)
                .forEach(memberIds::add);
        Map<Long, AdminMemberIdentityResponse> memberIdentities =
                memberIdentityReader.findByUserSns(memberIds);
        List<AdminServiceRequestListItemResponse> items = page.getItems().stream()
                .map(item -> {
                    AdminQuoteSummary quote = quoteSummaries.get(item.getServiceRequestId());
                    AdminServiceTradeSummary trade = flow.trades().get(item.getServiceRequestId());
                    AdminSettlementSummary settlement = settlementFor(trade, flow);
                    AdminEscrowSummary escrow = escrowFor(trade, flow);
                    validateCorrelation(item.getStatusCode(), quote, trade, settlement, escrow);
                    return AdminServiceRequestListItemResponse.from(
                            item,
                            quote,
                            trade,
                            settlement,
                            escrow,
                            integratedStatus(item.getStatusCode(), quote),
                            memberIdentities.get(item.getRequesterUserId()));
                })
                .toList();
        return new AdminServiceRequestPageResponse(
                items,
                page.getPage(),
                page.getSize(),
                page.getTotalItems(),
                page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminServiceRequestDetailResponse getDetail(Long serviceRequestId) {
        if (serviceRequestId == null || serviceRequestId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        AdminServiceRequestDetail detail = reader.readDetail(serviceRequestId);
        detail.setTitle(sensitiveDataMasker.maskText(detail.getTitle()));
        detail.setContent(sensitiveDataMasker.maskText(detail.getContent()));
        Map<Long, AdminQuoteSummary> quoteSummaries = quoteSummaryReader.findSummaries(List.of(serviceRequestId));
        if (!List.of(serviceRequestId).containsAll(quoteSummaries.keySet())) {
            throw inconsistent("조회 대상이 아닌 견적 요약이 반환되었습니다.");
        }
        AdminQuoteSummary quote = quoteSummaries.get(serviceRequestId);
        ServiceFlowSummaries flow = loadFlowSummaries(List.of(serviceRequestId));
        AdminServiceTradeSummary trade = flow.trades().get(serviceRequestId);
        AdminSettlementSummary settlement = settlementFor(trade, flow);
        AdminEscrowSummary escrow = escrowFor(trade, flow);
        validateCorrelation(detail.getStatusCode(), quote, trade, settlement, escrow);
        Long selectedProviderUserId = quote == null ? null : quote.getSelectedProviderUserId();
        Map<Long, AdminMemberIdentityResponse> identities = memberIdentityReader.findByUserSns(
                java.util.stream.Stream.of(detail.getRequesterUserId(), selectedProviderUserId)
                        .filter(java.util.Objects::nonNull)
                        .toList());
        return AdminServiceRequestDetailResponse.from(
                detail,
                quote,
                trade,
                settlement,
                escrow,
                integratedStatus(detail.getStatusCode(), quote),
                identities.get(detail.getRequesterUserId()),
                identities.get(selectedProviderUserId));
    }

    private ServiceFlowSummaries loadFlowSummaries(List<Long> serviceRequestIds) {
        if (serviceRequestIds.isEmpty()) {
            return ServiceFlowSummaries.empty();
        }
        Map<Long, AdminServiceTradeSummary> trades = tradeSummaryReader.findSummaries(serviceRequestIds);
        if (!serviceRequestIds.containsAll(trades.keySet())) {
            throw inconsistent("조회 대상이 아닌 서비스 거래가 반환되었습니다.");
        }
        List<Long> tradeIds = trades.values().stream()
                .map(AdminServiceTradeSummary::getTradeId)
                .distinct()
                .toList();
        Map<Long, AdminSettlementSummary> settlements = settlementSummaryReader.findSummaries(tradeIds);
        Map<Long, AdminEscrowSummary> escrows = escrowSummaryReader.findSummaries(tradeIds);
        if (!tradeIds.containsAll(settlements.keySet()) || !tradeIds.containsAll(escrows.keySet())) {
            throw inconsistent("조회 대상이 아닌 거래의 정산 또는 보관금 상태가 반환되었습니다.");
        }
        return new ServiceFlowSummaries(trades, settlements, escrows);
    }

    private AdminSettlementSummary settlementFor(
            AdminServiceTradeSummary trade,
            ServiceFlowSummaries flow) {
        return trade == null ? null : flow.settlements().get(trade.getTradeId());
    }

    private AdminEscrowSummary escrowFor(
            AdminServiceTradeSummary trade,
            ServiceFlowSummaries flow) {
        return trade == null ? null : flow.escrows().get(trade.getTradeId());
    }

    private void validateCorrelation(
            String sourceStatusCode,
            AdminQuoteSummary quote,
            AdminServiceTradeSummary trade,
            AdminSettlementSummary settlement,
            AdminEscrowSummary escrow) {
        Long selectedQuoteId = quote == null ? null : quote.getSelectedQuoteId();
        boolean hasSelectedQuote = selectedQuoteId != null;
        boolean hasTrade = trade != null;
        if (hasSelectedQuote != hasTrade
                || hasTrade && !selectedQuoteId.equals(trade.getQuoteId())
                || "SVCC0003".equals(sourceStatusCode) && !hasTrade
                || !"SVCC0003".equals(sourceStatusCode) && hasTrade) {
            throw inconsistent("서비스 요청·선택 견적·거래 연결이 일관되지 않습니다.");
        }
        if (!hasTrade) {
            return;
        }
        if (escrow == null
                || quote.getSelectedAmount() == null
                || escrow.getEscrowDebitedAmount() != quote.getSelectedAmount()) {
            throw inconsistent("서비스 거래의 보관금 원장이 선택 견적 금액과 일치하지 않습니다.");
        }
        validateSettlementLedger(trade, settlement, escrow);
    }

    private void validateSettlementLedger(
            AdminServiceTradeSummary trade,
            AdminSettlementSummary settlement,
            AdminEscrowSummary escrow) {
        String settlementStatus = settlement == null ? null : settlement.getStatusCode();
        boolean completedSettlement = "STLC0003".equals(settlementStatus);
        boolean refundedSettlement = "STLC0004".equals(settlementStatus);
        boolean refundLedgerMismatch = settlement != null
                && refundedSettlement != (escrow.getRefundedAmount() > 0);
        if (completedSettlement != (escrow.getSettledAmount() > 0)
                || refundLedgerMismatch) {
            throw inconsistent("서비스 거래의 보관금·정산 상태가 일관되지 않습니다.");
        }
        if ("STLC0001".equals(settlementStatus) || "STLC0002".equals(settlementStatus)) {
            if ("TRDC0008".equals(trade.getTradeStatusCode())
                    || escrow.activeEscrowAmount() <= 0) {
                throw inconsistent("취소 거래 또는 보관금 없는 거래는 정산 대기·보류일 수 없습니다.");
            }
            return;
        }
        if (completedSettlement && !"TRDC0006".equals(trade.getTradeStatusCode())) {
            throw inconsistent("정산 완료 상태와 거래 완료 상태가 일치하지 않습니다.");
        }
        if (refundedSettlement && !"TRDC0008".equals(trade.getTradeStatusCode())) {
            throw inconsistent("정산 환불 상태와 거래 취소 상태가 일치하지 않습니다.");
        }
        if (settlement == null) {
            boolean canceledTrade = "TRDC0008".equals(trade.getTradeStatusCode());
            boolean canceledWithoutRefund = canceledTrade && escrow.getRefundedAmount() <= 0;
            boolean activeTradeWithoutEscrow = !canceledTrade && escrow.activeEscrowAmount() <= 0;
            if ("TRDC0006".equals(trade.getTradeStatusCode())
                    || canceledWithoutRefund
                    || activeTradeWithoutEscrow) {
                throw inconsistent("정산이 없는 서비스 거래의 원장 상태가 일관되지 않습니다.");
            }
        }
    }

    private CustomException inconsistent(String message) {
        return new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, message);
    }

    private record ServiceFlowSummaries(
            Map<Long, AdminServiceTradeSummary> trades,
            Map<Long, AdminSettlementSummary> settlements,
            Map<Long, AdminEscrowSummary> escrows) {

        private static ServiceFlowSummaries empty() {
            return new ServiceFlowSummaries(Map.of(), Map.of(), Map.of());
        }
    }

    private AdminServiceRequestIntegratedStatus integratedStatus(
            String sourceStatusCode,
            AdminQuoteSummary quote) {
        if (sourceStatusCode == null) {
            throw new CustomException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "서비스 요청 상태가 없습니다.");
        }
        int activeQuoteCount = quote == null ? 0 : quote.getActiveQuoteCount();
        return switch (sourceStatusCode) {
            case "SVCC0001" -> new AdminServiceRequestIntegratedStatus("RECEIVED", "접수");
            case "SVCC0002" -> activeQuoteCount > 0
                    ? new AdminServiceRequestIntegratedStatus("IN_PROGRESS", "처리중")
                    : new AdminServiceRequestIntegratedStatus("RECEIVED", "접수");
            case "SVCC0003" -> new AdminServiceRequestIntegratedStatus("IN_PROGRESS", "처리중");
            case "SVCC0004" -> new AdminServiceRequestIntegratedStatus("COMPLETED", "완료");
            default -> throw new CustomException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "알 수 없는 서비스 요청 상태입니다: " + sourceStatusCode);
        };
    }

    private void normalize(AdminServiceRequestListRequest request) {
        request.setPage(Math.max(1, request.getPage()));
        request.setSize(request.getSize() <= 0 ? DEFAULT_SIZE : Math.min(request.getSize(), MAX_SIZE));
        request.setKeyword(trimToNull(request.getKeyword()));
        request.setStatusCode(trimToNull(request.getStatusCode()));

        if (request.getKeyword() != null && request.getKeyword().length() > MAX_KEYWORD_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.getCategorySn() != null && request.getCategorySn() <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.getCategorySn() != null && adminCategoryService
                .getCategories(SERVICE_CATEGORY_DOMAIN)
                .stream()
                .noneMatch(category -> category.categorySn().equals(request.getCategorySn()))) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        LocalDate registeredFrom = request.getRegisteredFrom();
        LocalDate registeredTo = request.getRegisteredTo();
        if (registeredFrom != null && registeredTo != null && registeredFrom.isAfter(registeredTo)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
