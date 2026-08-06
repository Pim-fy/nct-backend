package nct.quote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.service.ProviderAccessGuard;
import nct.quote.domain.Quote;
import nct.quote.dto.QuoteSubmitRequest;
import nct.quote.dto.QuoteResponse;
import nct.quote.mapper.QuoteMapper;
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
    private Authentication authentication;

    private QuoteService service;

    @BeforeEach
    void setUp() {
        service = new QuoteService(quoteMapper, serviceRequestQuoteReader, providerAccessGuard);
    }

    @Test
    void submitsOnlyAfterOpenRequestAndCategoryAccessChecks() {
        QuoteSubmitRequest request = new QuoteSubmitRequest(10L, "테스트 견적", 100_000L, "작업 범위", null);
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
    }

    @Test
    void rejectsNonOpenRequestBeforeInsert() {
        QuoteSubmitRequest request = new QuoteSubmitRequest(10L, "테스트 견적", 100_000L, null, null);
        when(serviceRequestQuoteReader.requireOpenForQuote(10L))
                .thenThrow(new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));

        assertThatThrownBy(() -> service.submitQuote(authentication, request))
                .isInstanceOf(CustomException.class);

        verifyNoInteractions(providerAccessGuard);
        verify(quoteMapper, never()).insertQuote(any(Quote.class));
    }

    @Test
    void rejectsSelfTradeAfterProviderAccessCheck() {
        QuoteSubmitRequest request = new QuoteSubmitRequest(10L, "테스트 견적", 100_000L, null, null);
        when(serviceRequestQuoteReader.requireOpenForQuote(10L))
                .thenReturn(new ServiceRequestQuoteTarget(22L, 20L));
        when(providerAccessGuard.requireServiceAccess(authentication, 20L)).thenReturn(22L);

        assertThatThrownBy(() -> service.submitQuote(authentication, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("본인이 등록한 서비스 요청");

        verify(quoteMapper, never()).insertQuote(any(Quote.class));
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

        assertThat(result.getContent().getFirst().getSvcReqTitle()).isEqualTo("이사 요청");
    }
}
