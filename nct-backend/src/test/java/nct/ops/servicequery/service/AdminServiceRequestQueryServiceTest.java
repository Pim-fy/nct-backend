package nct.ops.servicequery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nct.global.exception.CustomException;
import nct.ops.reference.dto.AdminCategoryResponse;
import nct.ops.reference.service.AdminCategoryService;
import nct.ops.servicequery.dto.AdminServiceRequestListRequest;
import nct.ops.security.service.SensitiveDataMasker;
import nct.member.port.AdminMemberIdentityReader;
import nct.point.dto.AdminEscrowSummary;
import nct.point.port.AdminEscrowSummaryReader;
import nct.quote.dto.AdminQuoteSummary;
import nct.quote.port.AdminQuoteSummaryReader;
import nct.settlement.dto.AdminSettlementSummary;
import nct.settlement.port.AdminSettlementSummaryReader;
import nct.servicerequest.dto.AdminServiceRequestListItem;
import nct.servicerequest.dto.AdminServiceRequestPage;
import nct.servicerequest.dto.AdminServiceRequestSearchCondition;
import nct.servicerequest.port.AdminServiceRequestReader;
import nct.trade.dto.AdminServiceTradeSummary;
import nct.trade.port.AdminServiceTradeSummaryReader;

/** 담당자 7: 관리자 서비스 요청 검색값의 정규화와 계약 경계를 검증한다. */
class AdminServiceRequestQueryServiceTest {
    private AdminServiceRequestReader reader;
    private AdminCategoryService adminCategoryService;
    private AdminQuoteSummaryReader quoteSummaryReader;
    private AdminServiceTradeSummaryReader tradeSummaryReader;
    private AdminSettlementSummaryReader settlementSummaryReader;
    private AdminEscrowSummaryReader escrowSummaryReader;
    private AdminServiceRequestQueryService service;

    @BeforeEach
    void setUp() {
        reader = mock(AdminServiceRequestReader.class);
        adminCategoryService = mock(AdminCategoryService.class);
        quoteSummaryReader = mock(AdminQuoteSummaryReader.class);
        tradeSummaryReader = mock(AdminServiceTradeSummaryReader.class);
        settlementSummaryReader = mock(AdminSettlementSummaryReader.class);
        escrowSummaryReader = mock(AdminEscrowSummaryReader.class);
        when(tradeSummaryReader.findSummaries(any())).thenReturn(Map.of());
        when(settlementSummaryReader.findSummaries(any())).thenReturn(Map.of());
        when(escrowSummaryReader.findSummaries(any())).thenReturn(Map.of());
        service = new AdminServiceRequestQueryService(
                reader,
                new SensitiveDataMasker(),
                adminCategoryService,
                quoteSummaryReader,
                tradeSummaryReader,
                settlementSummaryReader,
                escrowSummaryReader,
                mock(AdminMemberIdentityReader.class));
    }

    @Test
    void normalizesSearchAndDelegatesToReader() {
        AdminServiceRequestListRequest request = new AdminServiceRequestListRequest();
        request.setKeyword("  청소  ");
        request.setCategorySn(11L);
        request.setStatusCode("  SVCC0002  ");
        request.setPage(0);
        request.setSize(100);
        AdminServiceRequestListItem item = AdminServiceRequestListItem.builder()
                .serviceRequestId(1L)
                .title("연락처 010-1234-5678")
                .statusCode("SVCC0002")
                .build();
        AdminServiceRequestPage expected = AdminServiceRequestPage.builder()
                .items(List.of(item)).page(1).size(50).totalItems(1).totalPages(1).build();
        when(adminCategoryService.getCategories("CATC0002"))
                .thenReturn(List.of(new AdminCategoryResponse(
                        11L, "CATC0002", "청소", 10, true, true)));
        when(reader.readPage(any(AdminServiceRequestSearchCondition.class))).thenReturn(expected);
        AdminQuoteSummary quote = quoteSummary(1L, 2, 1, null);
        when(quoteSummaryReader.findSummaries(List.of(1L))).thenReturn(Map.of(1L, quote));

        var result = service.getPage(request);

        ArgumentCaptor<AdminServiceRequestSearchCondition> captor =
                ArgumentCaptor.forClass(AdminServiceRequestSearchCondition.class);
        verify(reader).readPage(captor.capture());
        assertThat(captor.getValue().getKeyword()).isEqualTo("청소");
        assertThat(captor.getValue().getCategorySn()).isEqualTo(11L);
        assertThat(captor.getValue().getStatusCode()).isEqualTo("SVCC0002");
        assertThat(captor.getValue().getPage()).isEqualTo(1);
        assertThat(captor.getValue().getSize()).isEqualTo(50);
        assertThat(result.items().get(0).title()).isEqualTo("연락처 [연락처 마스킹]");
        assertThat(result.items().get(0).integratedStatusCode()).isEqualTo("IN_PROGRESS");
        assertThat(result.items().get(0).totalQuoteCount()).isEqualTo(2);
        verify(quoteSummaryReader).findSummaries(List.of(1L));
    }

    @Test
    void rejectsInvalidDateRangeBeforeReading() {
        AdminServiceRequestListRequest request = new AdminServiceRequestListRequest();
        request.setRegisteredFrom(LocalDate.of(2026, 8, 2));
        request.setRegisteredTo(LocalDate.of(2026, 8, 1));

        assertThatThrownBy(() -> service.getPage(request)).isInstanceOf(CustomException.class);
        verify(reader, never()).readPage(any());
    }

    @Test
    void rejectsInvalidDetailIdBeforeReading() {
        assertThatThrownBy(() -> service.getDetail(0L)).isInstanceOf(CustomException.class);
        verify(reader, never()).readDetail(any());
    }

    @Test
    void rejectsCategoryOutsideServiceDomainBeforeReading() {
        AdminServiceRequestListRequest request = new AdminServiceRequestListRequest();
        request.setCategorySn(999L);
        when(adminCategoryService.getCategories("CATC0002")).thenReturn(List.of());

        assertThatThrownBy(() -> service.getPage(request)).isInstanceOf(CustomException.class);
        verify(reader, never()).readPage(any());
    }

    @Test
    void rejectsUnknownSourceStatusInsteadOfGuessingCompletion() {
        AdminServiceRequestListItem item = AdminServiceRequestListItem.builder()
                .serviceRequestId(3L)
                .title("알 수 없는 상태")
                .statusCode("SVCC9999")
                .build();
        when(reader.readPage(any())).thenReturn(AdminServiceRequestPage.builder()
                .items(List.of(item)).page(1).size(20).totalItems(1).totalPages(1).build());
        when(quoteSummaryReader.findSummaries(List.of(3L))).thenReturn(Map.of());

        assertThatThrownBy(() -> service.getPage(new AdminServiceRequestListRequest()))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void keepsOpenRequestReceivedWhenOnlyExpiredOrWithdrawnQuotesRemain() {
        AdminServiceRequestListItem item = AdminServiceRequestListItem.builder()
                .serviceRequestId(4L)
                .title("활성 견적 없음")
                .statusCode("SVCC0002")
                .build();
        when(reader.readPage(any())).thenReturn(AdminServiceRequestPage.builder()
                .items(List.of(item)).page(1).size(20).totalItems(1).totalPages(1).build());
        AdminQuoteSummary quote = quoteSummary(4L, 2, 0, null);
        when(quoteSummaryReader.findSummaries(List.of(4L))).thenReturn(Map.of(4L, quote));

        var result = service.getPage(new AdminServiceRequestListRequest());

        assertThat(result.items().get(0).integratedStatusCode()).isEqualTo("RECEIVED");
    }

    @Test
    void joinsTradeSettlementDisputeAndEscrowInBatch() {
        AdminServiceRequestListItem item = AdminServiceRequestListItem.builder()
                .serviceRequestId(5L)
                .title("매칭된 요청")
                .statusCode("SVCC0003")
                .build();
        when(reader.readPage(any())).thenReturn(AdminServiceRequestPage.builder()
                .items(List.of(item)).page(1).size(20).totalItems(1).totalPages(1).build());
        AdminQuoteSummary quote = quoteSummary(5L, 1, 0, 50L);
        when(quoteSummaryReader.findSummaries(List.of(5L))).thenReturn(Map.of(5L, quote));

        AdminServiceTradeSummary trade = new AdminServiceTradeSummary();
        trade.setServiceRequestId(5L);
        trade.setTradeId(500L);
        trade.setQuoteId(50L);
        trade.setTradeStatusCode("TRDC0007");
        trade.setTradeStatusName("보류");
        trade.setActiveDisputeCount(1);
        trade.setActiveDisputeId(700L);
        trade.setActiveDisputeStatusCode("TRDC0017");
        trade.setActiveDisputeStatusName("처리중");
        when(tradeSummaryReader.findSummaries(List.of(5L))).thenReturn(Map.of(5L, trade));

        AdminSettlementSummary settlement = new AdminSettlementSummary();
        settlement.setTradeId(500L);
        settlement.setSettlementId(600L);
        settlement.setStatusCode("STLC0002");
        settlement.setStatusName("보류");
        when(settlementSummaryReader.findSummaries(List.of(500L)))
                .thenReturn(Map.of(500L, settlement));

        AdminEscrowSummary escrow = new AdminEscrowSummary();
        escrow.setTradeId(500L);
        escrow.setEscrowDebitedAmount(30_000L);
        escrow.setEscrowLedgerAmount(-30_000L);
        when(escrowSummaryReader.findSummaries(List.of(500L))).thenReturn(Map.of(500L, escrow));

        var result = service.getPage(new AdminServiceRequestListRequest());

        var response = result.items().get(0);
        assertThat(response.tradeId()).isEqualTo(500L);
        assertThat(response.activeDisputeId()).isEqualTo(700L);
        assertThat(response.settlementStatusCode()).isEqualTo("STLC0002");
        assertThat(response.activeEscrowAmount()).isEqualTo(30_000L);
        verify(settlementSummaryReader).findSummaries(List.of(500L));
        verify(escrowSummaryReader).findSummaries(List.of(500L));
    }

    @Test
    void rejectsSelectedQuoteAndTradeMismatch() {
        AdminServiceRequestListItem item = AdminServiceRequestListItem.builder()
                .serviceRequestId(6L)
                .title("연결 불일치")
                .statusCode("SVCC0003")
                .build();
        when(reader.readPage(any())).thenReturn(AdminServiceRequestPage.builder()
                .items(List.of(item)).page(1).size(20).totalItems(1).totalPages(1).build());
        when(quoteSummaryReader.findSummaries(List.of(6L)))
                .thenReturn(Map.of(6L, quoteSummary(6L, 1, 0, 60L)));
        AdminServiceTradeSummary trade = new AdminServiceTradeSummary();
        trade.setServiceRequestId(6L);
        trade.setTradeId(600L);
        trade.setQuoteId(61L);
        trade.setTradeStatusCode("TRDC0003");
        when(tradeSummaryReader.findSummaries(List.of(6L))).thenReturn(Map.of(6L, trade));

        assertThatThrownBy(() -> service.getPage(new AdminServiceRequestListRequest()))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void rejectsClosedRequestWithSelectedTrade() {
        AdminServiceRequestListItem item = AdminServiceRequestListItem.builder()
                .serviceRequestId(7L)
                .title("종료 요청 연결 오류")
                .statusCode("SVCC0004")
                .build();
        when(reader.readPage(any())).thenReturn(AdminServiceRequestPage.builder()
                .items(List.of(item)).page(1).size(20).totalItems(1).totalPages(1).build());
        when(quoteSummaryReader.findSummaries(List.of(7L)))
                .thenReturn(Map.of(7L, quoteSummary(7L, 1, 0, 70L)));
        AdminServiceTradeSummary trade = new AdminServiceTradeSummary();
        trade.setServiceRequestId(7L);
        trade.setTradeId(700L);
        trade.setQuoteId(70L);
        trade.setTradeStatusCode("TRDC0003");
        when(tradeSummaryReader.findSummaries(List.of(7L))).thenReturn(Map.of(7L, trade));

        assertThatThrownBy(() -> service.getPage(new AdminServiceRequestListRequest()))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void rejectsMatchedTradeWithoutEscrowLedger() {
        AdminServiceRequestListItem item = AdminServiceRequestListItem.builder()
                .serviceRequestId(8L)
                .title("보관금 누락")
                .statusCode("SVCC0003")
                .build();
        when(reader.readPage(any())).thenReturn(AdminServiceRequestPage.builder()
                .items(List.of(item)).page(1).size(20).totalItems(1).totalPages(1).build());
        when(quoteSummaryReader.findSummaries(List.of(8L)))
                .thenReturn(Map.of(8L, quoteSummary(8L, 1, 0, 80L)));
        AdminServiceTradeSummary trade = new AdminServiceTradeSummary();
        trade.setServiceRequestId(8L);
        trade.setTradeId(800L);
        trade.setQuoteId(80L);
        trade.setTradeStatusCode("TRDC0003");
        when(tradeSummaryReader.findSummaries(List.of(8L))).thenReturn(Map.of(8L, trade));

        assertThatThrownBy(() -> service.getPage(new AdminServiceRequestListRequest()))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void acceptsCanceledTradeRefundedBeforeSettlementWasCreated() {
        AdminServiceRequestListItem item = AdminServiceRequestListItem.builder()
                .serviceRequestId(9L)
                .title("정산 전 환불")
                .statusCode("SVCC0003")
                .build();
        when(reader.readPage(any())).thenReturn(AdminServiceRequestPage.builder()
                .items(List.of(item)).page(1).size(20).totalItems(1).totalPages(1).build());
        when(quoteSummaryReader.findSummaries(List.of(9L)))
                .thenReturn(Map.of(9L, quoteSummary(9L, 1, 0, 90L)));
        AdminServiceTradeSummary trade = new AdminServiceTradeSummary();
        trade.setServiceRequestId(9L);
        trade.setTradeId(900L);
        trade.setQuoteId(90L);
        trade.setTradeStatusCode("TRDC0008");
        when(tradeSummaryReader.findSummaries(List.of(9L))).thenReturn(Map.of(9L, trade));
        AdminEscrowSummary escrow = new AdminEscrowSummary();
        escrow.setTradeId(900L);
        escrow.setEscrowDebitedAmount(30_000L);
        escrow.setRefundedAmount(30_000L);
        when(escrowSummaryReader.findSummaries(List.of(900L))).thenReturn(Map.of(900L, escrow));

        var result = service.getPage(new AdminServiceRequestListRequest());

        assertThat(result.items().get(0).tradeStatusCode()).isEqualTo("TRDC0008");
        assertThat(result.items().get(0).activeEscrowAmount()).isZero();
    }

    @Test
    void rejectsCanceledTradeWithPendingSettlementAndHeldEscrow() {
        AdminServiceRequestListItem item = AdminServiceRequestListItem.builder()
                .serviceRequestId(10L)
                .title("취소·정산 모순")
                .statusCode("SVCC0003")
                .build();
        when(reader.readPage(any())).thenReturn(AdminServiceRequestPage.builder()
                .items(List.of(item)).page(1).size(20).totalItems(1).totalPages(1).build());
        when(quoteSummaryReader.findSummaries(List.of(10L)))
                .thenReturn(Map.of(10L, quoteSummary(10L, 1, 0, 100L)));
        AdminServiceTradeSummary trade = new AdminServiceTradeSummary();
        trade.setServiceRequestId(10L);
        trade.setTradeId(1_000L);
        trade.setQuoteId(100L);
        trade.setTradeStatusCode("TRDC0008");
        when(tradeSummaryReader.findSummaries(List.of(10L))).thenReturn(Map.of(10L, trade));
        AdminSettlementSummary settlement = new AdminSettlementSummary();
        settlement.setTradeId(1_000L);
        settlement.setSettlementId(2_000L);
        settlement.setStatusCode("STLC0001");
        when(settlementSummaryReader.findSummaries(List.of(1_000L)))
                .thenReturn(Map.of(1_000L, settlement));
        AdminEscrowSummary escrow = new AdminEscrowSummary();
        escrow.setTradeId(1_000L);
        escrow.setEscrowDebitedAmount(30_000L);
        escrow.setEscrowLedgerAmount(-30_000L);
        when(escrowSummaryReader.findSummaries(List.of(1_000L)))
                .thenReturn(Map.of(1_000L, escrow));

        assertThatThrownBy(() -> service.getPage(new AdminServiceRequestListRequest()))
                .isInstanceOf(CustomException.class);
    }

    private AdminQuoteSummary quoteSummary(
            Long serviceRequestId,
            int totalQuoteCount,
            int activeQuoteCount,
            Long selectedQuoteId) {
        AdminQuoteSummary summary = new AdminQuoteSummary();
        summary.setServiceRequestId(serviceRequestId);
        summary.setTotalQuoteCount(totalQuoteCount);
        summary.setActiveQuoteCount(activeQuoteCount);
        if (selectedQuoteId != null) {
            summary.setSelectedQuoteCount(1);
            summary.setSelectedQuoteId(selectedQuoteId);
            summary.setSelectedProviderUserId(900L);
            summary.setSelectedAmount(30_000L);
            summary.setSelectedQuoteStatusCode("QUTC0004");
        }
        return summary;
    }
}
