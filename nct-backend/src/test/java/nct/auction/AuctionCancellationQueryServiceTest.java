package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.dto.AuctionPendingCancelRequestResponse;
import nct.auction.mapper.AuctionCancelRequestMapper;
import nct.auction.mapper.AuctionMapper;
import nct.auction.service.AuctionCancellationService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.service.ReferenceDataService;
import nct.point.service.PointService;
import nct.trade.service.TradeService;

@ExtendWith(MockitoExtension.class)
class AuctionCancellationQueryServiceTest {

    @Mock
    private AuctionMapper auctionMapper;

    @Mock
    private AuctionCancelRequestMapper cancelRequestMapper;

    @Mock
    private ReferenceDataService referenceDataService;

    @Mock
    private TradeService tradeService;

    @Mock
    private PointService pointService;

    @InjectMocks
    private AuctionCancellationService cancellationService;

    @Test
    void getsPendingCancellationRequestByAuctionId() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 7, 22, 15, 30);
        AuctionPendingCancelRequestResponse expected = pendingRequest(requestedAt);
        when(cancelRequestMapper.findPendingByAuctionId(1181L)).thenReturn(expected);

        AuctionPendingCancelRequestResponse actual =
                cancellationService.getPendingCancellationRequest(1181L);

        assertThat(actual.getCancelRequestSn()).isEqualTo(77L);
        assertThat(actual.getAucSn()).isEqualTo(1181L);
        assertThat(actual.getRequesterUsrSn()).isEqualTo(16395L);
        assertThat(actual.getReason()).isEqualTo("상품 상태 변경");
        assertThat(actual.getPreviousAuctionStatusCode()).isEqualTo(AuctionStatusCode.ENDED);
        assertThat(actual.getCurrentAuctionStatusCode()).isEqualTo(AuctionStatusCode.CANCEL_REQUESTED);
        assertThat(actual.getRequestedAt()).isEqualTo(requestedAt);
    }

    @Test
    void missingCancellationRequestIsNotReturned() {
        when(cancelRequestMapper.findPendingByAuctionId(1181L)).thenReturn(null);

        assertThatThrownBy(() -> cancellationService.getPendingCancellationRequest(1181L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUCTION_CANCEL_REQUEST_NOT_FOUND);
    }

    @Test
    void processedCancellationRequestIsExcludedFromPendingLookup() {
        when(cancelRequestMapper.findPendingByAuctionId(1181L)).thenReturn(null);

        assertThatThrownBy(() -> cancellationService.getPendingCancellationRequest(1181L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUCTION_CANCEL_REQUEST_NOT_FOUND);

        verify(cancelRequestMapper).findPendingByAuctionId(1181L);
    }

    @Test
    void rejectsInvalidAuctionIdBeforeQueryingMapper() {
        assertThatThrownBy(() -> cancellationService.getPendingCancellationRequest(0L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(cancelRequestMapper, never()).findPendingByAuctionId(0L);
    }

    private AuctionPendingCancelRequestResponse pendingRequest(LocalDateTime requestedAt) {
        AuctionPendingCancelRequestResponse response = new AuctionPendingCancelRequestResponse();
        response.setCancelRequestSn(77L);
        response.setAucSn(1181L);
        response.setRequesterUsrSn(16395L);
        response.setReason("상품 상태 변경");
        response.setPreviousAuctionStatusCode(AuctionStatusCode.ENDED);
        response.setCurrentAuctionStatusCode(AuctionStatusCode.CANCEL_REQUESTED);
        response.setRequestedAt(requestedAt);
        return response;
    }
}
