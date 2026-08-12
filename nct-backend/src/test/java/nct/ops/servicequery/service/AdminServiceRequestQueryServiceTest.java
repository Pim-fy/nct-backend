package nct.ops.servicequery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nct.global.exception.CustomException;
import nct.member.dto.AdminMemberIdentityResponse;
import nct.ops.reference.dto.AdminCategoryResponse;
import nct.ops.reference.service.AdminCategoryService;
import nct.ops.servicequery.dto.AdminServiceRequestListRequest;
import nct.ops.security.service.SensitiveDataMasker;
import nct.member.port.AdminMemberIdentityReader;
import nct.point.dto.AdminEscrowSummary;
import nct.point.port.AdminEscrowSummaryReader;
import nct.quote.dto.AdminQuoteListItem;
import nct.quote.dto.AdminQuoteSummary;
import nct.quote.port.AdminQuoteListReader;
import nct.quote.port.AdminQuoteSummaryReader;
import nct.settlement.dto.AdminSettlementSummary;
import nct.settlement.port.AdminSettlementSummaryReader;
import nct.servicerequest.dto.AdminServiceRequestDetail;
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
    private AdminQuoteListReader quoteListReader;
    private AdminServiceTradeSummaryReader tradeSummaryReader;
    private AdminSettlementSummaryReader settlementSummaryReader;
    private AdminEscrowSummaryReader escrowSummaryReader;
    private AdminMemberIdentityReader memberIdentityReader;
    private AdminServiceRequestQueryService service;

    @BeforeEach
    void setUp() {
        reader = mock(AdminServiceRequestReader.class);
        adminCategoryService = mock(AdminCategoryService.class);
        quoteSummaryReader = mock(AdminQuoteSummaryReader.class);
        quoteListReader = mock(AdminQuoteListReader.class);
        tradeSummaryReader = mock(AdminServiceTradeSummaryReader.class);
        settlementSummaryReader = mock(AdminSettlementSummaryReader.class);
        escrowSummaryReader = mock(AdminEscrowSummaryReader.class);
        memberIdentityReader = mock(AdminMemberIdentityReader.class);
        when(tradeSummaryReader.findSummaries(any())).thenReturn(Map.of());
        when(settlementSummaryReader.findSummaries(any())).thenReturn(Map.of());
        when(escrowSummaryReader.findSummaries(any())).thenReturn(Map.of());
        when(quoteListReader.findByServiceRequestId(any())).thenReturn(List.of());
        service = new AdminServiceRequestQueryService(
                reader,
                new SensitiveDataMasker(),
                adminCategoryService,
                quoteSummaryReader,
                quoteListReader,
                tradeSummaryReader,
                settlementSummaryReader,
                escrowSummaryReader,
                memberIdentityReader);
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
    void returnsDetailWhenNoQuoteHasBeenSelected() {
        AdminServiceRequestDetail detail = AdminServiceRequestDetail.builder()
                .serviceRequestId(11L)
                .requesterUserId(101L)
                .title("선택 견적 없는 요청")
                .statusCode("SVCC0002")
                .build();
        when(reader.readDetail(11L)).thenReturn(detail);
        when(quoteSummaryReader.findSummaries(List.of(11L))).thenReturn(Map.of());
        when(memberIdentityReader.findByUserSns(List.of(101L))).thenReturn(Map.of());

        var result = service.getDetail(11L);

        assertThat(result.serviceRequestId()).isEqualTo(11L);
        assertThat(result.integratedStatusCode()).isEqualTo("RECEIVED");
        assertThat(result.selectedProviderUserId()).isNull();
        assertThat(result.selectedProviderMember()).isNull();
        assertThat(result.quotes()).isEmpty();
        verify(quoteListReader).findByServiceRequestId(11L);
        verify(memberIdentityReader).findByUserSns(List.of(101L));
    }

    @Test
    void returnsDetailQuoteListWithOneProviderIdentityBatch() {
        AdminServiceRequestDetail detail = AdminServiceRequestDetail.builder()
                .serviceRequestId(12L)
                .requesterUserId(101L)
                .title("Open request")
                .statusCode("SVCC0002")
                .build();
        when(reader.readDetail(12L)).thenReturn(detail);
        AdminQuoteSummary summary = quoteSummary(12L, 2, 2, null);
        when(quoteSummaryReader.findSummaries(List.of(12L))).thenReturn(Map.of(12L, summary));
        AdminQuoteListItem first = quoteListItem(12L, 502L, 202L, 60_000L, "QUTC0002");
        AdminQuoteListItem second = quoteListItem(12L, 501L, 201L, 50_000L, "QUTC0001");
        when(quoteListReader.findByServiceRequestId(12L)).thenReturn(List.of(first, second));
        AdminMemberIdentityResponse requester = memberIdentity(101L, "requester");
        AdminMemberIdentityResponse providerOne = memberIdentity(201L, "provider-one");
        AdminMemberIdentityResponse providerTwo = memberIdentity(202L, "provider-two");
        when(memberIdentityReader.findByUserSns(List.of(101L, 202L, 201L)))
                .thenReturn(Map.of(101L, requester, 201L, providerOne, 202L, providerTwo));

        var result = service.getDetail(12L);

        assertThat(result.quotes()).hasSize(2);
        assertThat(result.quotes().getFirst().quoteId()).isEqualTo(502L);
        assertThat(result.quotes().getFirst().providerMember()).isSameAs(providerTwo);
        assertThat(result.quotes()).allMatch(quote -> !quote.selected());
        verify(memberIdentityReader).findByUserSns(List.of(101L, 202L, 201L));
    }

    @Test
    void marksSummarySelectedQuoteInDetailList() {
        AdminServiceRequestDetail detail = AdminServiceRequestDetail.builder()
                .serviceRequestId(13L)
                .requesterUserId(101L)
                .title("Matched request")
                .statusCode("SVCC0003")
                .build();
        when(reader.readDetail(13L)).thenReturn(detail);
        AdminQuoteSummary summary = quoteSummary(13L, 2, 0, 502L);
        when(quoteSummaryReader.findSummaries(List.of(13L))).thenReturn(Map.of(13L, summary));
        when(quoteListReader.findByServiceRequestId(13L)).thenReturn(List.of(
                quoteListItem(13L, 502L, 900L, 30_000L, "QUTC0004"),
                quoteListItem(13L, 501L, 201L, 25_000L, "QUTC0005")));
        AdminServiceTradeSummary trade = new AdminServiceTradeSummary();
        trade.setServiceRequestId(13L);
        trade.setTradeId(700L);
        trade.setQuoteId(502L);
        trade.setTradeStatusCode("TRDC0001");
        when(tradeSummaryReader.findSummaries(List.of(13L))).thenReturn(Map.of(13L, trade));
        AdminEscrowSummary escrow = new AdminEscrowSummary();
        escrow.setTradeId(700L);
        escrow.setEscrowDebitedAmount(30_000L);
        escrow.setEscrowLedgerAmount(-30_000L);
        when(escrowSummaryReader.findSummaries(List.of(700L))).thenReturn(Map.of(700L, escrow));
        AdminMemberIdentityResponse selectedProvider = memberIdentity(900L, "selected-provider");
        AdminMemberIdentityResponse competingProvider = memberIdentity(201L, "competing-provider");
        when(memberIdentityReader.findByUserSns(List.of(101L, 900L, 201L)))
                .thenReturn(Map.of(900L, selectedProvider, 201L, competingProvider));

        var result = service.getDetail(13L);

        assertThat(result.quotes()).filteredOn(quote -> quote.selected())
                .singleElement()
                .extracting(quote -> quote.quoteId())
                .isEqualTo(502L);
        assertThat(result.selectedProviderMember()).isSameAs(selectedProvider);
    }

    @Test
    void rejectsDetailWhenProviderIdentityBatchOmitsAQuoteProvider() {
        AdminServiceRequestDetail detail = AdminServiceRequestDetail.builder()
                .serviceRequestId(15L)
                .requesterUserId(101L)
                .statusCode("SVCC0002")
                .build();
        when(reader.readDetail(15L)).thenReturn(detail);
        AdminQuoteSummary summary = quoteSummary(15L, 1, 1, null);
        when(quoteSummaryReader.findSummaries(List.of(15L))).thenReturn(Map.of(15L, summary));
        when(quoteListReader.findByServiceRequestId(15L)).thenReturn(List.of(
                quoteListItem(15L, 501L, 201L, 25_000L, "QUTC0001")));
        when(memberIdentityReader.findByUserSns(List.of(101L, 201L)))
                .thenReturn(Map.of(101L, memberIdentity(101L, "requester")));

        assertThatThrownBy(() -> service.getDetail(15L))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void rejectsDetailWhenQuoteSummaryAndListCountsDiffer() {
        AdminServiceRequestDetail detail = AdminServiceRequestDetail.builder()
                .serviceRequestId(14L)
                .requesterUserId(101L)
                .statusCode("SVCC0002")
                .build();
        when(reader.readDetail(14L)).thenReturn(detail);
        AdminQuoteSummary summary = quoteSummary(14L, 1, 1, null);
        when(quoteSummaryReader.findSummaries(List.of(14L))).thenReturn(Map.of(14L, summary));

        assertThatThrownBy(() -> service.getDetail(14L))
                .isInstanceOf(CustomException.class);
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
        trade.setActiveDisputeStatusCode("ABSC0002");
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

    private AdminQuoteListItem quoteListItem(
            Long serviceRequestId,
            Long quoteId,
            Long providerUserId,
            Long amount,
            String statusCode) {
        AdminQuoteListItem item = new AdminQuoteListItem();
        item.setServiceRequestId(serviceRequestId);
        item.setQuoteId(quoteId);
        item.setProviderUserId(providerUserId);
        item.setAmount(amount);
        item.setStatusCode(statusCode);
        item.setSubmittedAt(LocalDateTime.of(2026, 8, 10, 14, 0));
        item.setUpdatedAt(LocalDateTime.of(2026, 8, 10, 14, 0));
        return item;
    }

    private AdminMemberIdentityResponse memberIdentity(Long userSn, String nickname) {
        return AdminMemberIdentityResponse.builder()
                .userSn(userSn)
                .nickname(nickname)
                .build();
    }
}
