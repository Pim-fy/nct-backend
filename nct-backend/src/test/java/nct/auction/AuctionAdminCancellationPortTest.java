package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.dto.AuctionBidTarget;
import nct.auction.mapper.AuctionCancelRequestMapper;
import nct.auction.mapper.AuctionMapper;
import nct.auction.port.AdminAuctionCancellationCommand;
import nct.auction.port.AdminAuctionCancellationResult;
import nct.auction.service.AuctionCancellationService;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.operation.port.SellerCancellationDecision;
import nct.ops.operation.port.SellerCancellationDecisionCommand;
import nct.ops.reference.service.ReferenceDataService;
import nct.point.service.PointService;
import nct.trade.dto.AuctionTradeEscrowInfo;
import nct.trade.service.TradeService;

@ExtendWith(MockitoExtension.class)
class AuctionAdminCancellationPortTest {

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
    private AuctionCancellationService service;

    @Test
    void cancelsReadyAuctionWithoutCreatingSellerRequest() {
        AuctionBidTarget auction = auction(11L, AuctionStatusCode.READY, null, null);
        when(auctionMapper.findAuctionBidTargetForUpdate(11L)).thenReturn(auction);
        when(auctionMapper.updateAuctionStatusForCancellation(
                11L, AuctionStatusCode.READY, AuctionStatusCode.CANCELED, "7"))
                .thenReturn(1);

        AdminAuctionCancellationResult result = service.cancel(command(11L));

        assertThat(result.previousStatusCode()).isEqualTo(AuctionStatusCode.READY);
        assertThat(result.statusCode()).isEqualTo(AuctionStatusCode.CANCELED);
        assertThat(result.changed()).isTrue();
        verifyNoInteractions(cancelRequestMapper, tradeService, pointService);
    }

    @Test
    void cancelsActiveAuctionAndReleasesHighestBidHold() {
        AuctionBidTarget auction = auction(12L, AuctionStatusCode.ACTIVE, 101L, 21L);
        when(auctionMapper.findAuctionBidTargetForUpdate(12L)).thenReturn(auction);
        when(auctionMapper.exceptionCancelHighestBid(12L, 101L, "7")).thenReturn(1);
        when(auctionMapper.updateAuctionStatusForCancellation(
                12L, AuctionStatusCode.ACTIVE, AuctionStatusCode.CANCELED, "7"))
                .thenReturn(1);

        service.cancel(command(12L));

        verify(pointService).releaseHold(
                21L,
                RefType.BID,
                101L,
                "경매 취소 승인 홀딩 반환: 허위 매물 신고 확인");
        verifyNoInteractions(cancelRequestMapper, tradeService);
    }

    @Test
    void cancelsEndedAuctionThroughTradeCancellationContract() {
        AuctionBidTarget auction = auction(13L, AuctionStatusCode.ENDED, 102L, 22L);
        auction.setProductId(301L);

        AuctionTradeEscrowInfo escrow = new AuctionTradeEscrowInfo();
        escrow.setTradeSn(401L);
        escrow.setBidSn(102L);
        escrow.setBuyerUsrSn(22L);

        when(auctionMapper.findAuctionBidTargetForUpdate(13L)).thenReturn(auction);
        when(tradeService.findAuctionTradeEscrowInfoByProductId(301L))
                .thenReturn(Optional.of(escrow));
        when(auctionMapper.exceptionCancelHighestBid(13L, 102L, "7")).thenReturn(1);
        when(auctionMapper.updateAuctionStatusForCancellation(
                13L, AuctionStatusCode.ENDED, AuctionStatusCode.CANCELED, "7"))
                .thenReturn(1);

        service.cancel(command(13L));

        ArgumentCaptor<SellerCancellationDecisionCommand> captor =
                ArgumentCaptor.forClass(SellerCancellationDecisionCommand.class);
        verify(tradeService).decide(captor.capture());
        assertThat(captor.getValue().tradeSn()).isEqualTo(401L);
        assertThat(captor.getValue().decision()).isEqualTo(SellerCancellationDecision.APPROVED);
        assertThat(captor.getValue().requestId()).isEqualTo("admin-auction-cancel:11");
        verify(pointService, never()).releaseHold(any(Long.class), any(), any(Long.class), any());
    }

    @Test
    void rejectsEndedAuctionWhenWinningBidAndEscrowDoNotMatch() {
        AuctionBidTarget auction = auction(14L, AuctionStatusCode.ENDED, 102L, 22L);
        auction.setProductId(301L);

        AuctionTradeEscrowInfo escrow = new AuctionTradeEscrowInfo();
        escrow.setTradeSn(401L);
        escrow.setBidSn(999L);
        escrow.setBuyerUsrSn(22L);

        when(auctionMapper.findAuctionBidTargetForUpdate(14L)).thenReturn(auction);
        when(tradeService.findAuctionTradeEscrowInfoByProductId(301L))
                .thenReturn(Optional.of(escrow));

        assertThatThrownBy(() -> service.cancel(command(14L)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        verify(tradeService, never()).decide(any());
        verify(auctionMapper, never()).exceptionCancelHighestBid(any(), any(), any());
        verify(auctionMapper, never()).updateAuctionStatusForCancellation(
                any(Long.class), any(), any(), any());
    }

    @Test
    void rejectsSellerCancellationPendingState() {
        when(auctionMapper.findAuctionBidTargetForUpdate(15L))
                .thenReturn(auction(15L, AuctionStatusCode.CANCEL_REQUESTED, null, null));

        assertThatThrownBy(() -> service.cancel(command(15L)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_CANCEL_INVALID_STATUS);

        verify(auctionMapper, never()).updateAuctionStatusForCancellation(
                any(Long.class), any(), any(), any());
        verifyNoInteractions(cancelRequestMapper, tradeService, pointService);
    }

    @Test
    void retriesAlreadyCanceledAuctionWithoutRepeatingSideEffects() {
        when(auctionMapper.findAuctionBidTargetForUpdate(16L))
                .thenReturn(auction(16L, AuctionStatusCode.CANCELED, 103L, 23L));

        AdminAuctionCancellationResult result = service.cancel(command(16L));

        assertThat(result.previousStatusCode()).isEqualTo(AuctionStatusCode.CANCELED);
        assertThat(result.statusCode()).isEqualTo(AuctionStatusCode.CANCELED);
        assertThat(result.changed()).isFalse();
        verifyNoInteractions(cancelRequestMapper, referenceDataService, tradeService, pointService);
        verify(auctionMapper, never()).exceptionCancelHighestBid(any(), any(), any());
        verify(auctionMapper, never()).updateAuctionStatusForCancellation(
                any(Long.class), any(), any(), any());
    }

    @Test
    void rejectsBlankRequestIdBeforeLockingAuction() {
        AdminAuctionCancellationCommand invalid = new AdminAuctionCancellationCommand(
                17L, 7L, "허위 매물 신고 확인", " ");

        assertThatThrownBy(() -> service.cancel(invalid))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(auctionMapper, cancelRequestMapper, referenceDataService, tradeService, pointService);
    }

    private AdminAuctionCancellationCommand command(long auctionId) {
        return new AdminAuctionCancellationCommand(
                auctionId,
                7L,
                " 허위 매물 신고 확인 ",
                " admin-auction-cancel:11 ");
    }

    private AuctionBidTarget auction(
            long auctionId,
            String statusCode,
            Long highestBidId,
            Long highestBidderId) {
        AuctionBidTarget auction = new AuctionBidTarget();
        auction.setAuctionId(auctionId);
        auction.setAuctionStatusCode(statusCode);
        auction.setCurrentHighestBidId(highestBidId);
        auction.setCurrentHighestBidderId(highestBidderId);
        return auction;
    }
}
