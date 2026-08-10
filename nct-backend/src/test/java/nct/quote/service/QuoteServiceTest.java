package nct.quote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.service.ProviderAccessGuard;
import nct.quote.domain.Quote;
import nct.quote.dto.AdminQuoteSummary;
import nct.quote.port.QuoteSelectionPort.SelectedQuoteResult;
import nct.quote.dto.QuoteAttachmentResponse;
import nct.quote.dto.QuoteSubmitRequest;
import nct.quote.dto.QuoteUpdateRequest;
import nct.quote.dto.QuoteResponse;
import nct.quote.mapper.QuoteMapper;
import nct.provider.service.ActiveProviderGuard;
import nct.servicerequest.port.ServiceRequestQuoteReader;
import nct.servicerequest.port.ServiceRequestQuoteReader.ServiceRequestQuoteTarget;

/** 담당자 7 통합: F-SVC-005 공개 상태·카테고리 권한·자기거래 견적 제출 회귀 테스트. */
@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    @Mock
    private QuoteMapper quoteMapper;
    @Mock
    private ServiceRequestQuoteReader serviceRequestQuoteReader;
    @Mock
    private ProviderAccessGuard providerAccessGuard;
    @Mock
    private ActiveProviderGuard activeProviderGuard;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private Authentication authentication;

    private QuoteService service;

    @BeforeEach
    void setUp() {
        service = new QuoteService(
                quoteMapper,
                serviceRequestQuoteReader,
                providerAccessGuard,
                activeProviderGuard,
                fileStorageService);
    }

    @Test
    void submitsOnlyAfterOpenRequestAndCategoryAccessChecks() {
        QuoteSubmitRequest request = new QuoteSubmitRequest(10L, null, 100_000L, "작업 범위", List.of(88L));
        when(serviceRequestQuoteReader.requireOpenForQuote(10L))
                .thenReturn(new ServiceRequestQuoteTarget(11L, 20L));
        when(providerAccessGuard.requireServiceAccess(authentication, 20L)).thenReturn(22L);
        when(quoteMapper.insertQuote(any(Quote.class))).thenAnswer(invocation -> {
            Quote quote = invocation.getArgument(0);
            quote.setQutSn(99L);
            return 1;
        });

        var result = service.submitQuote(authentication, request);

        assertThat(result.qutSn()).isEqualTo(99L);
        verify(providerAccessGuard).requireServiceAccess(authentication, 20L);
        verify(fileStorageService).requireOwnedQuoteFile(88L, 22L);
        verify(quoteMapper).insertQuotePhoto(any());
    }

    @Test
    void rejectsNonOpenRequestBeforeInsert() {
        QuoteSubmitRequest request = new QuoteSubmitRequest(10L, null, 100_000L, null, null);
        when(serviceRequestQuoteReader.requireOpenForQuote(10L))
                .thenThrow(new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));

        assertThatThrownBy(() -> service.submitQuote(authentication, request))
                .isInstanceOf(CustomException.class);

        verifyNoInteractions(providerAccessGuard);
        verify(quoteMapper, never()).insertQuote(any(Quote.class));
    }

    @Test
    void rejectsSelfTradeAfterProviderAccessCheck() {
        QuoteSubmitRequest request = new QuoteSubmitRequest(10L, null, 100_000L, null, null);
        when(serviceRequestQuoteReader.requireOpenForQuote(10L))
                .thenReturn(new ServiceRequestQuoteTarget(22L, 20L));
        when(providerAccessGuard.requireServiceAccess(authentication, 20L)).thenReturn(22L);

        assertThatThrownBy(() -> service.submitQuote(authentication, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("본인이 등록한 서비스 요청");

        verify(quoteMapper, never()).insertQuote(any(Quote.class));
    }

    @Test
    void updateQuoteRevalidatesCurrentCategoryAccessBeforeMutation() {
        Quote quote = Quote.builder()
                .qutSn(99L)
                .svcReqSn(10L)
                .usrSn(7L)
                .qutAmt(100_000L)
                .qutCn("기존 작업 범위")
                .qutStatusCd("QUTC0001")
                .qutReviseCnt(0)
                .build();
        QuoteUpdateRequest request = new QuoteUpdateRequest(
                "수정 견적",
                120_000L,
                "수정 작업 범위",
                List.of(88L));
        when(quoteMapper.findQuoteByIdForUpdate(99L)).thenReturn(quote);
        when(serviceRequestQuoteReader.requireForProviderAccess(10L))
                .thenReturn(new ServiceRequestQuoteTarget(11L, 20L));
        when(quoteMapper.updateQuote(99L, request, "7")).thenReturn(1);

        service.updateQuote(7L, 99L, request);

        verify(serviceRequestQuoteReader).requireForProviderAccess(10L);
        verify(activeProviderGuard).requireActiveForCategory(7L, 20L);
        verify(quoteMapper).insertQuoteHistory(any());
        verify(quoteMapper).updateQuote(99L, request, "7");
        verify(fileStorageService).requireOwnedQuoteFile(88L, 7L);
    }

    @Test
    void updateQuoteStopsBeforeMutationWhenCurrentCategoryAccessIsBlocked() {
        Quote quote = Quote.builder()
                .qutSn(99L)
                .svcReqSn(10L)
                .usrSn(7L)
                .qutStatusCd("QUTC0001")
                .build();
        QuoteUpdateRequest request = new QuoteUpdateRequest(
                "수정 견적",
                120_000L,
                "수정 작업 범위",
                List.of(88L));
        when(quoteMapper.findQuoteByIdForUpdate(99L)).thenReturn(quote);
        when(serviceRequestQuoteReader.requireForProviderAccess(10L))
                .thenReturn(new ServiceRequestQuoteTarget(11L, 20L));
        doThrow(new CustomException(ErrorCode.FORBIDDEN))
                .when(activeProviderGuard).requireActiveForCategory(7L, 20L);

        assertThatThrownBy(() -> service.updateQuote(7L, 99L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(quoteMapper, never()).insertQuoteHistory(any());
        verify(quoteMapper, never()).updateQuote(any(), any(), any());
        verify(quoteMapper, never()).deleteQuotePhotosByQutSn(any());
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void withdrawQuoteRevalidatesCurrentCategoryAccess() {
        Quote quote = Quote.builder()
                .qutSn(99L)
                .svcReqSn(10L)
                .usrSn(7L)
                .qutStatusCd("QUTC0002")
                .build();
        when(quoteMapper.findQuoteByIdForUpdate(99L)).thenReturn(quote);
        when(serviceRequestQuoteReader.requireForProviderAccess(10L))
                .thenReturn(new ServiceRequestQuoteTarget(11L, 20L));
        when(quoteMapper.withdrawQuote(99L, "7")).thenReturn(1);

        service.withdrawQuote(7L, 99L);

        verify(activeProviderGuard).requireActiveForCategory(7L, 20L);
        verify(quoteMapper).withdrawQuote(99L, "7");
    }

    @Test
    void withdrawQuoteStopsBeforeMutationWhenCurrentCategoryAccessIsBlocked() {
        Quote quote = Quote.builder()
                .qutSn(99L)
                .svcReqSn(10L)
                .usrSn(7L)
                .qutStatusCd("QUTC0001")
                .build();
        when(quoteMapper.findQuoteByIdForUpdate(99L)).thenReturn(quote);
        when(serviceRequestQuoteReader.requireForProviderAccess(10L))
                .thenReturn(new ServiceRequestQuoteTarget(11L, 20L));
        doThrow(new CustomException(ErrorCode.FORBIDDEN))
                .when(activeProviderGuard).requireActiveForCategory(7L, 20L);

        assertThatThrownBy(() -> service.withdrawQuote(7L, 99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(quoteMapper, never()).withdrawQuote(any(), any());
    }

    @Test
    void returnsAdminSummariesInOneMapperCall() {
        AdminQuoteSummary first = adminSummary(10L, 2, 1, 0, null);
        AdminQuoteSummary second = adminSummary(11L, 3, 0, 1, 99L);
        second.setSelectedProviderUserId(22L);
        second.setSelectedAmount(120_000L);
        second.setSelectedQuoteStatusCode("QUTC0004");
        when(quoteMapper.findAdminSummaries(List.of(10L, 11L))).thenReturn(List.of(first, second));

        Map<Long, AdminQuoteSummary> result = service.findSummaries(List.of(10L, 11L, 10L));

        assertThat(result).containsOnlyKeys(10L, 11L);
        assertThat(result.get(11L).getSelectedQuoteId()).isEqualTo(99L);
        verify(quoteMapper).findAdminSummaries(List.of(10L, 11L));
    }

    @Test
    void rejectsMultipleSelectedQuotesInAdminSummary() {
        AdminQuoteSummary inconsistent = adminSummary(10L, 2, 0, 2, 99L);
        inconsistent.setSelectedProviderUserId(22L);
        inconsistent.setSelectedAmount(120_000L);
        inconsistent.setSelectedQuoteStatusCode("QUTC0004");
        when(quoteMapper.findAdminSummaries(List.of(10L))).thenReturn(List.of(inconsistent));

        assertThatThrownBy(() -> service.findSummaries(List.of(10L)))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void rejectsUnsupportedQuoteStatusInAdminSummary() {
        AdminQuoteSummary inconsistent = adminSummary(10L, 1, 0, 0, null);
        inconsistent.setUnsupportedQuoteCount(1);
        when(quoteMapper.findAdminSummaries(List.of(10L))).thenReturn(List.of(inconsistent));

        assertThatThrownBy(() -> service.findSummaries(List.of(10L)))
                .isInstanceOf(CustomException.class);
    }

    private AdminQuoteSummary adminSummary(
            Long serviceRequestId,
            int totalQuoteCount,
            int activeQuoteCount,
            int selectedQuoteCount,
            Long selectedQuoteId) {
        AdminQuoteSummary summary = new AdminQuoteSummary();
        summary.setServiceRequestId(serviceRequestId);
        summary.setTotalQuoteCount(totalQuoteCount);
        summary.setActiveQuoteCount(activeQuoteCount);
        summary.setSelectedQuoteCount(selectedQuoteCount);
        summary.setSelectedQuoteId(selectedQuoteId);
        return summary;
    }

    @Test
    void receivedQuotesUseServiceRequestOwnerContract() {
        when(quoteMapper.findQuotesBySvcReqSn(10L)).thenReturn(List.of());

        service.getReceivedQuotes(7L, 10L);

        verify(serviceRequestQuoteReader).requireOwner(10L, 7L);
        verify(quoteMapper).findQuotesBySvcReqSn(10L);
    }

    @Test
    void myQuotesReceiveTitlesThroughServiceRequestReader() {
        QuoteResponse quote = new QuoteResponse();
        quote.setSvcReqSn(10L);
        when(quoteMapper.findMyQuotes(7L, 0, 10)).thenReturn(List.of(quote));
        when(serviceRequestQuoteReader.findTitles(List.of(10L))).thenReturn(Map.of(10L, "이사 요청"));
        when(quoteMapper.countMyQuotes(7L)).thenReturn(1);

        var result = service.getMyQuotes(7L, 1, 10);

        verify(activeProviderGuard).requireActive(7L);

        assertThat(result.getContent().getFirst().getSvcReqTitle()).isEqualTo("이사 요청");
    }

    @Test
    void myQuotesStopBeforeReadWhenProviderIsNotActive() {
        doThrow(new CustomException(ErrorCode.FORBIDDEN))
                .when(activeProviderGuard).requireActive(7L);

        assertThatThrownBy(() -> service.getMyQuotes(7L, 1, 10))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(quoteMapper, serviceRequestQuoteReader, fileStorageService);
    }

    @Test
    void myQuoteSummaryReturnsActiveQuoteCount() {
        when(quoteMapper.countMyActiveQuotes(7L)).thenReturn(2);

        var result = service.getMyQuoteSummary(7L);

        assertThat(result.activeQuoteCount()).isEqualTo(2);
        verify(activeProviderGuard).requireActive(7L);
        verify(quoteMapper).countMyActiveQuotes(7L);
    }

    @Test
    void myQuoteSummaryRejectsInvalidUserNumber() {
        assertThatThrownBy(() -> service.getMyQuoteSummary(0L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(activeProviderGuard, quoteMapper);
    }

    @Test
    void myQuoteSummaryStopsWhenProviderIsNotActive() {
        doThrow(new CustomException(ErrorCode.FORBIDDEN))
                .when(activeProviderGuard).requireActive(7L);

        assertThatThrownBy(() -> service.getMyQuoteSummary(7L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(quoteMapper);
    }

    @Test
    void activeQuoteReturnsOnlyProtectedAttachmentUrls() {
        QuoteResponse quote = new QuoteResponse();
        quote.setQutSn(99L);
        QuoteAttachmentResponse attachment = new QuoteAttachmentResponse();
        attachment.setFlSn(88L);
        attachment.setFileName("견적서.pdf");
        when(serviceRequestQuoteReader.requireForProviderAccess(10L))
                .thenReturn(new ServiceRequestQuoteTarget(11L, 20L));
        when(quoteMapper.findMyActiveQuote(7L, 10L)).thenReturn(quote);
        when(quoteMapper.findQuoteAttachments(99L)).thenReturn(List.of(attachment));

        QuoteResponse result = service.getMyActiveQuote(7L, 10L);

        verify(activeProviderGuard).requireActiveForCategory(7L, 20L);

        assertThat(result.getAttachments()).singleElement()
                .extracting(QuoteAttachmentResponse::getUrl)
                .isEqualTo("/api/quotes/99/attachments/88");
    }

    @Test
    void activeQuoteStopsBeforeReadWhenCurrentCategoryAccessIsBlocked() {
        when(serviceRequestQuoteReader.requireForProviderAccess(10L))
                .thenReturn(new ServiceRequestQuoteTarget(11L, 20L));
        doThrow(new CustomException(ErrorCode.FORBIDDEN))
                .when(activeProviderGuard).requireActiveForCategory(7L, 20L);

        assertThatThrownBy(() -> service.getMyActiveQuote(7L, 10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(quoteMapper, fileStorageService);
    }

    @Test
    void selectQuoteTransitionsToSelectedAndWithdrawsCompetitors() {
        Quote quote = Quote.builder()
                .qutSn(1L).svcReqSn(10L).usrSn(22L).qutAmt(100_000L)
                .qutStatusCd("QUTC0001").build();
        when(quoteMapper.findQuoteByIdForUpdate(1L)).thenReturn(quote);
        when(quoteMapper.selectQuote(1L, "7")).thenReturn(1);

        SelectedQuoteResult result = service.selectQuote(1L, 10L, 7L);

        assertThat(result.qutSn()).isEqualTo(1L);
        assertThat(result.providerUsrSn()).isEqualTo(22L);
        assertThat(result.amount()).isEqualTo(100_000L);
        verify(quoteMapper).selectQuote(1L, "7");
        verify(quoteMapper).withdrawCompetingQuotes(10L, 1L, "7");
    }

    @Test
    void selectQuoteIsIdempotentWhenAlreadySelected() {
        Quote quote = Quote.builder()
                .qutSn(1L).svcReqSn(10L).usrSn(22L).qutAmt(100_000L)
                .qutStatusCd("QUTC0004").build();
        when(quoteMapper.findQuoteByIdForUpdate(1L)).thenReturn(quote);

        SelectedQuoteResult result = service.selectQuote(1L, 10L, 7L);

        assertThat(result.qutSn()).isEqualTo(1L);
        verify(quoteMapper, never()).selectQuote(any(), any());
        verify(quoteMapper, never()).withdrawCompetingQuotes(any(), any(), any());
    }

    @Test
    void selectQuoteBlocksNonOwnerOfServiceRequest() {
        Quote quote = Quote.builder()
                .qutSn(1L).svcReqSn(10L).usrSn(22L)
                .qutStatusCd("QUTC0001").build();
        when(quoteMapper.findQuoteByIdForUpdate(1L)).thenReturn(quote);
        doThrow(new CustomException(ErrorCode.NOT_RESOURCE_OWNER))
                .when(serviceRequestQuoteReader).requireOwner(10L, 7L);

        assertThatThrownBy(() -> service.selectQuote(1L, 10L, 7L))
                .isInstanceOf(CustomException.class);

        verify(quoteMapper, never()).selectQuote(any(), any());
    }

    @Test
    void selectQuoteRejectsWithdrawnQuote() {
        Quote quote = Quote.builder()
                .qutSn(1L).svcReqSn(10L).usrSn(22L)
                .qutStatusCd("QUTC0005").build();
        when(quoteMapper.findQuoteByIdForUpdate(1L)).thenReturn(quote);

        assertThatThrownBy(() -> service.selectQuote(1L, 10L, 7L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("현재 상태에서 허용되지 않는 견적 처리입니다.");

        verify(quoteMapper, never()).selectQuote(any(), any());
    }

    @Test
    void blocksQuoteAttachmentWhenViewerIsNeitherProviderNorRequester() {
        Quote quote = Quote.builder()
                .qutSn(99L)
                .svcReqSn(10L)
                .usrSn(22L)
                .build();
        when(quoteMapper.findQuoteById(99L)).thenReturn(quote);
        when(quoteMapper.countQuoteAttachment(99L, 88L)).thenReturn(1);
        doThrow(new CustomException(ErrorCode.NOT_RESOURCE_OWNER))
                .when(serviceRequestQuoteReader).requireOwner(10L, 7L);

        assertThatThrownBy(() -> service.requireAttachmentAccess(7L, 99L, 88L))
                .isInstanceOf(CustomException.class);

        verify(serviceRequestQuoteReader).requireOwner(10L, 7L);
    }
}
