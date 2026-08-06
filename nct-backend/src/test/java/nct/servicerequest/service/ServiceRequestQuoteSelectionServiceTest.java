package nct.servicerequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import nct.provider.service.ActiveProviderGuard;
import nct.quote.port.QuoteSelectionPort;
import nct.quote.port.QuoteSelectionPort.SelectedQuoteResult;
import nct.servicerequest.domain.ServiceRequest;
import nct.servicerequest.dto.ServiceRequestQuoteSelectionResponse;
import nct.servicerequest.mapper.ServiceRequestMapper;
import nct.trade.dto.ServiceTradeCreateResult;
import nct.trade.service.ServiceTradeCreationCoordinator;

class ServiceRequestQuoteSelectionServiceTest {

    @Test
    void selectsQuoteMarksRequestMatchedThenCreatesTrade() {
        ServiceRequestMapper requestMapper = mock(ServiceRequestMapper.class);
        QuoteSelectionPort quoteSelectionPort = mock(QuoteSelectionPort.class);
        ActiveProviderGuard activeProviderGuard = mock(ActiveProviderGuard.class);
        ServiceTradeCreationCoordinator tradeCreationCoordinator = mock(ServiceTradeCreationCoordinator.class);
        ServiceRequestQuoteSelectionService service = new ServiceRequestQuoteSelectionService(
                requestMapper,
                quoteSelectionPort,
                activeProviderGuard,
                tradeCreationCoordinator);
        ServiceRequest request = ServiceRequest.builder()
                .svcReqSn(31L)
                .usrSn(11L)
                .catSn(7L)
                .svcReqStatusCd("SVCC0002")
                .svcReqUseYn('Y')
                .svcReqBdgtAmt(BigDecimal.valueOf(150000L))
                .build();
        when(requestMapper.findServiceRequestEntityByIdForUpdate(31L)).thenReturn(Optional.of(request));
        when(quoteSelectionPort.selectQuote(41L, 31L, 11L))
                .thenReturn(new SelectedQuoteResult(41L, 22L, 150000L));
        when(requestMapper.markServiceRequestMatched(31L, 11L, "11")).thenReturn(1);
        when(tradeCreationCoordinator.create(11L, 31L, 41L))
                .thenReturn(new ServiceTradeCreateResult(91L, "TRDC0003", true));

        ServiceRequestQuoteSelectionResponse result = service.selectQuoteAndCreateTrade(31L, 41L, 11L);

        assertThat(result.tradeId()).isEqualTo(91L);
        InOrder order = inOrder(quoteSelectionPort, activeProviderGuard, requestMapper, tradeCreationCoordinator);
        order.verify(quoteSelectionPort).selectQuote(41L, 31L, 11L);
        order.verify(activeProviderGuard).requireActiveForCategory(22L, 7L);
        order.verify(requestMapper).markServiceRequestMatched(31L, 11L, "11");
        order.verify(tradeCreationCoordinator).create(11L, 31L, 41L);
    }

    @Test
    void returnsExistingTradeForAlreadyMatchedRequestWithoutSelectingAgain() {
        ServiceRequestMapper requestMapper = mock(ServiceRequestMapper.class);
        QuoteSelectionPort quoteSelectionPort = mock(QuoteSelectionPort.class);
        ActiveProviderGuard activeProviderGuard = mock(ActiveProviderGuard.class);
        ServiceTradeCreationCoordinator tradeCreationCoordinator = mock(ServiceTradeCreationCoordinator.class);
        ServiceRequestQuoteSelectionService service = new ServiceRequestQuoteSelectionService(
                requestMapper,
                quoteSelectionPort,
                activeProviderGuard,
                tradeCreationCoordinator);
        ServiceRequest request = ServiceRequest.builder()
                .svcReqSn(31L)
                .usrSn(11L)
                .catSn(7L)
                .svcReqStatusCd("SVCC0003")
                .svcReqUseYn('Y')
                .build();
        when(requestMapper.findServiceRequestEntityByIdForUpdate(31L)).thenReturn(Optional.of(request));
        when(tradeCreationCoordinator.create(11L, 31L, 41L))
                .thenReturn(new ServiceTradeCreateResult(91L, "TRDC0003", false));

        ServiceRequestQuoteSelectionResponse result = service.selectQuoteAndCreateTrade(31L, 41L, 11L);

        assertThat(result.tradeId()).isEqualTo(91L);
        verify(quoteSelectionPort, never()).selectQuote(any(), any(), any());
        verify(activeProviderGuard, never()).requireActiveForCategory(any(), any());
        verify(requestMapper, never()).markServiceRequestMatched(any(), any(), any());
        verify(tradeCreationCoordinator).create(11L, 31L, 41L);
    }
}
