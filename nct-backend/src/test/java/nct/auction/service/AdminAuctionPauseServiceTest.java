package nct.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.dto.AuctionBidTarget;
import nct.auction.mapper.AuctionMapper;
import nct.auction.port.AdminAuctionPauseCommand;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.service.ReferenceDataService;
import nct.ops.sanction.port.SanctionStatusReader;

/** 담당자 7 · F-OPS-003: 관리자 수동 일시중지와 재개의 상태 경계를 검증합니다. */
class AdminAuctionPauseServiceTest {

    private AuctionMapper auctionMapper;
    private ReferenceDataService referenceDataService;
    private SanctionStatusReader sanctionStatusReader;
    private AdminAuctionPauseService service;

    @BeforeEach
    void setUp() {
        auctionMapper = mock(AuctionMapper.class);
        referenceDataService = mock(ReferenceDataService.class);
        sanctionStatusReader = mock(SanctionStatusReader.class);
        service = new AdminAuctionPauseService(
                auctionMapper,
                referenceDataService,
                sanctionStatusReader);
    }

    @Test
    void pausesOnlyActiveAuction() {
        when(auctionMapper.findAuctionBidTargetForUpdate(81L))
                .thenReturn(target(AuctionStatusCode.ACTIVE));
        when(auctionMapper.pauseAuctionForAdmin(81L, "7")).thenReturn(1);

        var result = service.pause(new AdminAuctionPauseCommand(81L, 7L));

        assertThat(result.changed()).isTrue();
        assertThat(result.previousStatusCode()).isEqualTo(AuctionStatusCode.ACTIVE);
        assertThat(result.statusCode()).isEqualTo(AuctionStatusCode.ADMIN_PAUSED);
        verify(referenceDataService).requireActiveCode("AUCG01", AuctionStatusCode.ADMIN_PAUSED);
    }

    @Test
    void repeatedPauseIsIdempotent() {
        when(auctionMapper.findAuctionBidTargetForUpdate(81L))
                .thenReturn(target(AuctionStatusCode.ADMIN_PAUSED));

        var result = service.pause(new AdminAuctionPauseCommand(81L, 7L));

        assertThat(result.changed()).isFalse();
        verify(auctionMapper, never()).pauseAuctionForAdmin(81L, "7");
    }

    @Test
    void resumesOnlyAdminPausedAuction() {
        when(auctionMapper.findAuctionBidTargetForUpdate(81L))
                .thenReturn(target(AuctionStatusCode.ADMIN_PAUSED));
        when(auctionMapper.resumeAuctionAfterAdminPause(81L, "7")).thenReturn(1);

        var result = service.resume(new AdminAuctionPauseCommand(81L, 7L));

        assertThat(result.changed()).isTrue();
        assertThat(result.statusCode()).isEqualTo(AuctionStatusCode.ACTIVE);
        verify(sanctionStatusReader).requireNoActiveSanction(11L);
        verify(referenceDataService).requireActiveCode("AUCG01", AuctionStatusCode.ACTIVE);
    }

    @Test
    void rejectsResumeWhileSellerIsSanctioned() {
        when(auctionMapper.findAuctionBidTargetForUpdate(81L))
                .thenReturn(target(AuctionStatusCode.ADMIN_PAUSED));
        doThrow(new CustomException(ErrorCode.FORBIDDEN))
                .when(sanctionStatusReader).requireNoActiveSanction(11L);

        assertThatThrownBy(() -> service.resume(new AdminAuctionPauseCommand(81L, 7L)))
                .isInstanceOf(CustomException.class);

        verify(auctionMapper, never()).resumeAuctionAfterAdminPause(81L, "7");
    }

    @Test
    void rejectsPauseOutsideActiveState() {
        when(auctionMapper.findAuctionBidTargetForUpdate(81L))
                .thenReturn(target(AuctionStatusCode.ENDED));

        assertThatThrownBy(() -> service.pause(new AdminAuctionPauseCommand(81L, 7L)))
                .isInstanceOf(CustomException.class);
        verify(auctionMapper, never()).pauseAuctionForAdmin(81L, "7");
    }

    @Test
    void rejectsPauseAfterAuctionEndTime() {
        AuctionBidTarget target = target(AuctionStatusCode.ACTIVE);
        target.setDatabaseNow(LocalDateTime.of(2026, 8, 13, 15, 0));
        target.setEndDateTime(LocalDateTime.of(2026, 8, 13, 14, 59));
        when(auctionMapper.findAuctionBidTargetForUpdate(81L)).thenReturn(target);

        assertThatThrownBy(() -> service.pause(new AdminAuctionPauseCommand(81L, 7L)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("종료 시각");

        verify(auctionMapper, never()).pauseAuctionForAdmin(81L, "7");
    }

    private AuctionBidTarget target(String statusCode) {
        AuctionBidTarget target = new AuctionBidTarget();
        target.setAuctionId(81L);
        target.setSellerId(11L);
        target.setAuctionStatusCode(statusCode);
        return target;
    }
}
