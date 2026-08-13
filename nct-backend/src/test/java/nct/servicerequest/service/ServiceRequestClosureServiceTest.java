package nct.servicerequest.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.quote.port.ServiceRequestQuoteExpirationPort;

/** 담당자 7 통합 · F-SVC-003: 요청 마감과 활성 견적 만료의 원자적 호출 순서를 검증합니다. */
@ExtendWith(MockitoExtension.class)
class ServiceRequestClosureServiceTest {

    @Mock
    private ServiceRequestService serviceRequestService;
    @Mock
    private ServiceRequestQuoteExpirationPort quoteExpirationPort;

    private ServiceRequestClosureService service;

    @BeforeEach
    void setUp() {
        service = new ServiceRequestClosureService(serviceRequestService, quoteExpirationPort);
    }

    @Test
    void requesterCloseExpiresQuotesAfterRequestStateTransition() {
        service.closeByRequester(10L, 7L);

        InOrder order = inOrder(serviceRequestService, quoteExpirationPort);
        order.verify(serviceRequestService).closeServiceRequest(10L, 7L);
        order.verify(quoteExpirationPort).expireActiveQuotes(10L, "7");
    }

    @Test
    void automaticCloseExpiresQuotesOnlyWhenRequestWasActuallyClosed() {
        when(serviceRequestService.autoCloseExpiredServiceRequest(10L)).thenReturn(true);
        when(serviceRequestService.autoCloseExpiredServiceRequest(11L)).thenReturn(false);

        service.closeExpired(10L);
        service.closeExpired(11L);

        verify(quoteExpirationPort).expireActiveQuotes(10L, "SYSTEM");
        verify(quoteExpirationPort, never()).expireActiveQuotes(11L, "SYSTEM");
    }
}
