package nct.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nct.abuse.port.ActiveAbuseReportReferenceReader;
import nct.auction.constant.AuctionStatusCode;
import nct.auction.dto.AuctionSanctionTarget;
import nct.auction.exception.AuctionCancellationReviewRequiredException;
import nct.auction.mapper.AuctionMapper;
import nct.auction.port.AdminAuctionCancellationPort;
import nct.auction.port.AdminAuctionCancellationResult;
import nct.auction.port.AuctionEnforcementImpact;
import nct.auction.port.MemberAuctionEnforcementCommand;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.service.ReferenceDataService;
import nct.point.service.PointService;
import nct.trade.port.ActiveTradeIncidentReader;

/** 담당자 7 · F-OPS-007: 영구정지 대상의 경매 취소와 입찰 홀드 반환 경계를 검증합니다. */
class AuctionSanctionEnforcementServiceTest {

    private AuctionMapper auctionMapper;
    private AdminAuctionCancellationPort cancellationPort;
    private PointService pointService;
    private ActiveAbuseReportReferenceReader activeReportReferenceReader;
    private ActiveTradeIncidentReader activeTradeIncidentReader;
    private AuctionSanctionEnforcementService service;

    @BeforeEach
    void setUp() {
        auctionMapper = mock(AuctionMapper.class);
        cancellationPort = mock(AdminAuctionCancellationPort.class);
        pointService = mock(PointService.class);
        activeReportReferenceReader = mock(ActiveAbuseReportReferenceReader.class);
        activeTradeIncidentReader = mock(ActiveTradeIncidentReader.class);
        service = new AuctionSanctionEnforcementService(
                auctionMapper,
                mock(ReferenceDataService.class),
                cancellationPort,
                pointService,
                activeReportReferenceReader,
                activeTradeIncidentReader);
    }

    @Test
    void permanentSuspensionCancelsOnlyHighestBidForBidderOwnedImpact() {
        AuctionSanctionTarget target = target(101L, 20L, 701L, 11L, AuctionStatusCode.ACTIVE);
        when(auctionMapper.findSanctionTargetsByMemberForUpdate(11L)).thenReturn(List.of(target));
        when(auctionMapper.exceptionCancelHighestBid(101L, 701L, "99")).thenReturn(1);

        List<AuctionEnforcementImpact> impacts = service.cancelForPermanentSuspension(command(11L));

        assertThat(impacts).singleElement().satisfies(impact -> {
            assertThat(impact.roleCode()).isEqualTo("HIGHEST_BIDDER");
            assertThat(impact.actionCode()).isEqualTo("BID_CANCELED");
        });
        verify(cancellationPort, never()).cancel(any());
        verify(pointService).releaseHold(
                eq(11L),
                eq(RefType.BID),
                eq(701L),
                contains("repeated unsafe trade"));
    }

    @Test
    void permanentSuspensionCancelsHighestBidWhileAuctionIsAdminPaused() {
        AuctionSanctionTarget target = target(
                101L,
                20L,
                701L,
                11L,
                AuctionStatusCode.ADMIN_PAUSED);
        when(auctionMapper.findSanctionTargetsByMemberForUpdate(11L)).thenReturn(List.of(target));
        when(auctionMapper.exceptionCancelHighestBid(101L, 701L, "99")).thenReturn(1);

        List<AuctionEnforcementImpact> impacts = service.cancelForPermanentSuspension(command(11L));

        assertThat(impacts).singleElement().satisfies(impact ->
                assertThat(impact.actionCode()).isEqualTo("BID_CANCELED"));
        verify(cancellationPort, never()).cancel(any());
        verify(pointService).releaseHold(eq(11L), eq(RefType.BID), eq(701L), any());
    }

    @Test
    void permanentSuspensionCancelsSellerOwnedActiveAuctionThroughExistingPort() {
        AuctionSanctionTarget target = target(101L, 11L, 701L, 20L, AuctionStatusCode.ACTIVE);
        when(auctionMapper.findSanctionTargetsByMemberForUpdate(11L)).thenReturn(List.of(target));
        when(cancellationPort.cancel(any())).thenReturn(new AdminAuctionCancellationResult(
                101L,
                AuctionStatusCode.ACTIVE,
                AuctionStatusCode.CANCELED,
                true));

        List<AuctionEnforcementImpact> impacts = service.cancelForPermanentSuspension(command(11L));

        assertThat(impacts).singleElement().satisfies(impact -> {
            assertThat(impact.roleCode()).isEqualTo("SELLER");
            assertThat(impact.actionCode()).isEqualTo("CANCELED");
        });
        verify(cancellationPort).cancel(any());
        verify(pointService, never()).releaseHold(
                anyLong(),
                any(),
                anyLong(),
                contains("permanent"));
    }

    @Test
    void permanentSuspensionHoldsSellerAuctionWhenCancellationPreconditionChanged() {
        AuctionSanctionTarget target = target(102L, 11L, 702L, 20L, AuctionStatusCode.ACTIVE);
        when(auctionMapper.findSanctionTargetsByMemberForUpdate(11L)).thenReturn(List.of(target));
        when(cancellationPort.cancel(any())).thenThrow(
                new AuctionCancellationReviewRequiredException("경매 상태가 이미 변경되었습니다."));

        List<AuctionEnforcementImpact> impacts =
                service.cancelForPermanentSuspension(command(11L));

        assertThat(impacts).singleElement().satisfies(impact -> {
            assertThat(impact.actionCode()).isEqualTo("HELD_FOR_REVIEW");
            assertThat(impact.result()).contains("관리자 검토");
        });
        verify(pointService, never()).releaseHold(anyLong(), any(), anyLong(), any());
    }

    @Test
    void permanentSuspensionStillPropagatesDatabaseOrMoneyFailure() {
        AuctionSanctionTarget target = target(103L, 11L, 703L, 20L, AuctionStatusCode.ACTIVE);
        when(auctionMapper.findSanctionTargetsByMemberForUpdate(11L)).thenReturn(List.of(target));
        when(cancellationPort.cancel(any())).thenThrow(
                new CustomException(ErrorCode.DATABASE_ERROR));

        assertThatThrownBy(() -> service.cancelForPermanentSuspension(command(11L)))
                .isInstanceOf(CustomException.class)
                .satisfies(error -> assertThat(((CustomException) error).getErrorCode())
                        .isEqualTo(ErrorCode.DATABASE_ERROR));
    }

    @Test
    void permanentSuspensionHoldsBidWhenHighestBidAlreadyChanged() {
        AuctionSanctionTarget target = target(104L, 20L, 704L, 11L, AuctionStatusCode.ACTIVE);
        when(auctionMapper.findSanctionTargetsByMemberForUpdate(11L)).thenReturn(List.of(target));
        when(auctionMapper.exceptionCancelHighestBid(104L, 704L, "99")).thenReturn(0);

        List<AuctionEnforcementImpact> impacts =
                service.cancelForPermanentSuspension(command(11L));

        assertThat(impacts).singleElement().satisfies(impact ->
                assertThat(impact.actionCode()).isEqualTo("HELD_FOR_REVIEW"));
        verify(pointService, never()).releaseHold(anyLong(), any(), anyLong(), any());
    }

    @Test
    void temporarySuspensionPausesAuctionWithoutCancelingBidOrReleasingHold() {
        AuctionSanctionTarget target = target(101L, 20L, 701L, 11L, AuctionStatusCode.ACTIVE);
        when(auctionMapper.findSanctionTargetsByMemberForUpdate(11L)).thenReturn(List.of(target));
        when(auctionMapper.pauseAuctionForSanction(101L, AuctionStatusCode.ACTIVE, "99"))
                .thenReturn(1);

        List<AuctionEnforcementImpact> impacts = service.pause(
                new MemberAuctionEnforcementCommand(
                        11L,
                        99L,
                        "temporary restriction",
                        "report-sanction-auction-pause-test",
                        java.time.LocalDateTime.now().plusDays(7),
                        501L));

        assertThat(impacts).singleElement().satisfies(impact -> {
            assertThat(impact.roleCode()).isEqualTo("HIGHEST_BIDDER");
            assertThat(impact.actionCode()).isEqualTo("PAUSED");
            assertThat(impact.previousStatusCode()).isEqualTo(AuctionStatusCode.ACTIVE);
        });
        verify(auctionMapper).pauseAuctionForSanction(101L, AuctionStatusCode.ACTIVE, "99");
        verify(auctionMapper, never()).exceptionCancelHighestBid(anyLong(), anyLong(), any());
        verify(pointService, never()).releaseHold(anyLong(), any(), anyLong(), any());
    }

    @Test
    void temporarySuspensionPreservesAdminPauseAsRestorableState() {
        AuctionSanctionTarget target = target(
                101L,
                20L,
                701L,
                11L,
                AuctionStatusCode.ADMIN_PAUSED);
        LocalDateTime pausedAt = LocalDateTime.of(2026, 8, 13, 10, 0);
        target.setUpdatedAt(pausedAt);
        target.setEndAt(pausedAt.plusMinutes(30));
        target.setDatabaseNow(pausedAt.plusMinutes(10));
        when(auctionMapper.findSanctionTargetsByMemberForUpdate(11L)).thenReturn(List.of(target));
        when(auctionMapper.pauseAuctionForSanction(101L, AuctionStatusCode.ADMIN_PAUSED, "99"))
                .thenReturn(1);

        List<AuctionEnforcementImpact> impacts = service.pause(
                new MemberAuctionEnforcementCommand(
                        11L,
                        99L,
                        "temporary restriction",
                        "report-sanction-admin-paused-bid-test",
                        java.time.LocalDateTime.now().plusDays(7),
                        501L));

        assertThat(impacts).singleElement().satisfies(impact -> {
            assertThat(impact.actionCode()).isEqualTo("PAUSED");
            assertThat(impact.previousStatusCode()).isEqualTo(AuctionStatusCode.ADMIN_PAUSED);
            assertThat(impact.remainingSeconds()).isEqualTo(1800L);
        });
        verify(auctionMapper).pauseAuctionForSanction(
                101L, AuctionStatusCode.ADMIN_PAUSED, "99");
        verify(auctionMapper, never()).exceptionCancelHighestBid(anyLong(), anyLong(), any());
        verify(pointService, never()).releaseHold(anyLong(), any(), anyLong(), any());
    }

    @Test
    void permanentSuspensionHoldsAuctionWhenAnotherReportIsOpen() {
        AuctionSanctionTarget target = target(101L, 11L, 701L, 20L, AuctionStatusCode.ACTIVE);
        when(auctionMapper.findSanctionTargetsByMemberForUpdate(11L)).thenReturn(List.of(target));
        when(activeReportReferenceReader.hasOtherActiveReportLinkedToAuction(101L, 501L))
                .thenReturn(true);

        List<AuctionEnforcementImpact> impacts = service.cancelForPermanentSuspension(
                new MemberAuctionEnforcementCommand(
                        11L,
                        99L,
                        "permanent restriction",
                        "report-sanction-auction-conflict-test",
                        null,
                        501L));

        assertThat(impacts).singleElement().satisfies(impact ->
                assertThat(impact.actionCode()).isEqualTo("HELD_FOR_REVIEW"));
        verify(cancellationPort, never()).cancel(any());
        verify(pointService, never()).releaseHold(anyLong(), any(), anyLong(), any());
    }

    @Test
    void permanentSuspensionCancelsEndedAuctionHeldByCurrentReport() {
        AuctionSanctionTarget target = target(101L, 11L, 701L, 20L, AuctionStatusCode.ENDED);
        target.setTradeSn(801L);
        target.setTradeStatusCode("TRDC0007");
        when(auctionMapper.findSanctionTargetsByMemberForUpdate(11L)).thenReturn(List.of(target));
        when(cancellationPort.cancel(any())).thenReturn(new AdminAuctionCancellationResult(
                101L,
                AuctionStatusCode.ENDED,
                AuctionStatusCode.CANCELED,
                true));

        service.cancelForPermanentSuspension(new MemberAuctionEnforcementCommand(
                11L,
                99L,
                "permanent restriction",
                "report-sanction-auction-current-report-test",
                null,
                501L));

        ArgumentCaptor<nct.auction.port.AdminAuctionCancellationCommand> captor =
                ArgumentCaptor.forClass(nct.auction.port.AdminAuctionCancellationCommand.class);
        verify(cancellationPort).cancel(captor.capture());
        assertThat(captor.getValue().sourceReportSn()).isEqualTo(501L);
        verify(activeTradeIncidentReader).hasOtherOpenIncident(801L, 501L);
    }

    private MemberAuctionEnforcementCommand command(Long userSn) {
        return new MemberAuctionEnforcementCommand(
                userSn,
                99L,
                "repeated unsafe trade",
                "report-sanction-auction-test",
                null);
    }

    private AuctionSanctionTarget target(
            Long auctionId,
            Long sellerUserSn,
            Long bidId,
            Long bidderUserSn,
            String statusCode) {
        AuctionSanctionTarget target = new AuctionSanctionTarget();
        target.setAuctionId(auctionId);
        target.setSellerUserSn(sellerUserSn);
        target.setHighestBidId(bidId);
        target.setHighestBidderUserSn(bidderUserSn);
        target.setAuctionStatusCode(statusCode);
        return target;
    }
}
