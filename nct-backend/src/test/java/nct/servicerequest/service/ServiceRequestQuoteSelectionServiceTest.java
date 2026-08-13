package nct.servicerequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.notification.service.NotificationService;
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
        NotificationService notificationService = mock(NotificationService.class);
        ServiceRequestQuoteSelectionService service = new ServiceRequestQuoteSelectionService(
                requestMapper,
                quoteSelectionPort,
                activeProviderGuard,
                tradeCreationCoordinator,
                notificationService);
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
        InOrder order = inOrder(
                quoteSelectionPort, activeProviderGuard, requestMapper,
                tradeCreationCoordinator, notificationService);
        order.verify(quoteSelectionPort).selectQuote(41L, 31L, 11L);
        order.verify(activeProviderGuard).requireActiveForCategory(22L, 7L);
        order.verify(requestMapper).markServiceRequestMatched(31L, 11L, "11");
        order.verify(tradeCreationCoordinator).create(11L, 31L, 41L);
        // 견적 선택 알림은 거래 생성 뒤 생성된 거래번호·견적금액으로 발행된다 (2026-08-13 이동 버튼 수정)
        order.verify(notificationService).notifyQuoteSelected(22L, 91L, 150000L);
    }

    @Test
    void returnsExistingTradeForAlreadyMatchedRequestWithoutSelectingAgain() {
        ServiceRequestMapper requestMapper = mock(ServiceRequestMapper.class);
        QuoteSelectionPort quoteSelectionPort = mock(QuoteSelectionPort.class);
        ActiveProviderGuard activeProviderGuard = mock(ActiveProviderGuard.class);
        ServiceTradeCreationCoordinator tradeCreationCoordinator = mock(ServiceTradeCreationCoordinator.class);
        NotificationService notificationService = mock(NotificationService.class);
        ServiceRequestQuoteSelectionService service = new ServiceRequestQuoteSelectionService(
                requestMapper,
                quoteSelectionPort,
                activeProviderGuard,
                tradeCreationCoordinator,
                notificationService);
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
        // 매칭 완료 재호출(멱등 경로)에서는 선택 알림을 다시 보내지 않는다
        verify(notificationService, never()).notifyQuoteSelected(anyLong(), anyLong(), anyLong());
    }

    // 잔액부족: 요청서는 이미 매칭완료로 전이된 뒤 담당자6 보관금 계약(코디네이터 내부)이
    // 잔액부족으로 실패하는 경우 — 메서드 전체가 @Transactional이라 이 예외가 그대로 전파되면
    // 스프링이 markServiceRequestMatched까지 포함해 트랜잭션 전체를 롤백한다. 여기서는 예외가
    // 삼켜지지 않고 그대로 올라가는지, 트레이드 ID를 담은 정상 응답을 반환하지 않는지만 검증한다.
    @Test
    void propagatesInsufficientBalanceFailureFromTradeCreationCoordinator() {
        ServiceRequestMapper requestMapper = mock(ServiceRequestMapper.class);
        QuoteSelectionPort quoteSelectionPort = mock(QuoteSelectionPort.class);
        ActiveProviderGuard activeProviderGuard = mock(ActiveProviderGuard.class);
        ServiceTradeCreationCoordinator tradeCreationCoordinator = mock(ServiceTradeCreationCoordinator.class);
        NotificationService notificationService = mock(NotificationService.class);
        ServiceRequestQuoteSelectionService service = new ServiceRequestQuoteSelectionService(
                requestMapper,
                quoteSelectionPort,
                activeProviderGuard,
                tradeCreationCoordinator,
                notificationService);
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
                .thenThrow(new CustomException(ErrorCode.POINT_INSUFFICIENT));

        assertThatThrownBy(() -> service.selectQuoteAndCreateTrade(31L, 41L, 11L))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.POINT_INSUFFICIENT);

        InOrder order = inOrder(quoteSelectionPort, requestMapper, tradeCreationCoordinator);
        order.verify(quoteSelectionPort).selectQuote(41L, 31L, 11L);
        order.verify(requestMapper).markServiceRequestMatched(31L, 11L, "11");
        order.verify(tradeCreationCoordinator).create(11L, 31L, 41L);
        // 거래 생성이 실패하면 선택 알림도 발행되지 않는다 (알림이 거래 생성 뒤로 이동했기 때문)
        verify(notificationService, never()).notifyQuoteSelected(anyLong(), anyLong(), anyLong());
    }

    // 부분실패: 견적 잠금(selectQuote)까지는 성공했지만, 그 사이 동시 요청으로 서비스 요청서
    // 상태가 바뀌어 markServiceRequestMatched의 UPDATE가 0행에 매치되는 경우(낙관적 잠금 실패).
    // 이후 단계(활성 제공자 검증·거래 생성)는 아예 호출되지 않고 즉시 CONFLICT로 중단돼야 한다.
    @Test
    void throwsConflictAndSkipsTradeCreationWhenMatchUpdateRacesWithConcurrentChange() {
        ServiceRequestMapper requestMapper = mock(ServiceRequestMapper.class);
        QuoteSelectionPort quoteSelectionPort = mock(QuoteSelectionPort.class);
        ActiveProviderGuard activeProviderGuard = mock(ActiveProviderGuard.class);
        ServiceTradeCreationCoordinator tradeCreationCoordinator = mock(ServiceTradeCreationCoordinator.class);
        NotificationService notificationService = mock(NotificationService.class);
        ServiceRequestQuoteSelectionService service = new ServiceRequestQuoteSelectionService(
                requestMapper,
                quoteSelectionPort,
                activeProviderGuard,
                tradeCreationCoordinator,
                notificationService);
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
        when(requestMapper.markServiceRequestMatched(31L, 11L, "11")).thenReturn(0);

        assertThatThrownBy(() -> service.selectQuoteAndCreateTrade(31L, 41L, 11L))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        verify(tradeCreationCoordinator, never()).create(anyLong(), anyLong(), anyLong());
    }
}
