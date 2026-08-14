package nct.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.abuse.port.ReportTargetHoldResult;
import nct.abuse.port.ReportTargetRestoreCommand;
import nct.auction.constant.AuctionStatusCode;
import nct.auction.dto.AuctionSanctionTarget;
import nct.auction.mapper.AuctionMapper;
import nct.ops.reference.service.ReferenceDataService;

/** 담당자 7 · F-OPS-007: 신고된 경매의 보류 시간 보존과 복구 계약을 검증합니다. */
class AuctionReportTargetHoldServiceTest {

    private AuctionMapper auctionMapper;
    private ReferenceDataService referenceDataService;
    private AuctionReportTargetHoldService service;

    @BeforeEach
    void setUp() {
        auctionMapper = mock(AuctionMapper.class);
        referenceDataService = mock(ReferenceDataService.class);
        service = new AuctionReportTargetHoldService(auctionMapper, referenceDataService);
    }

    @Test
    void pausesActiveAuctionAndPreservesRemainingTime() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 14, 10, 0);
        AuctionSanctionTarget target = new AuctionSanctionTarget();
        target.setAuctionId(81L);
        target.setAuctionStatusCode(AuctionStatusCode.ACTIVE);
        target.setStartAt(now.minusHours(1));
        target.setEndAt(now.plusHours(2));
        target.setDatabaseNow(now);
        when(auctionMapper.findReportHoldTargetForUpdate(81L)).thenReturn(target);
        when(auctionMapper.pauseAuctionForSanction(81L, AuctionStatusCode.ACTIVE, "10"))
                .thenReturn(1);

        ReportTargetHoldResult result = service.pause(81L, "10");

        assertThat(result.changed()).isTrue();
        assertThat(result.previousStatusCode()).isEqualTo(AuctionStatusCode.ACTIVE);
        assertThat(result.remainingSeconds()).isEqualTo(7200L);
        verify(referenceDataService).requireActiveCode("AUCG01", AuctionStatusCode.OPERATION_HOLD);
        verify(auctionMapper).pauseAuctionForSanction(81L, AuctionStatusCode.ACTIVE, "10");
    }

    @Test
    void restoresAuctionWithPreservedRemainingTime() {
        ReportTargetRestoreCommand command = new ReportTargetRestoreCommand(
                81L, AuctionStatusCode.ACTIVE, null, 7200L, "10");
        when(auctionMapper.restoreAuctionAfterSanction(
                81L, AuctionStatusCode.ACTIVE, null, 7200L, "10"))
                .thenReturn(1);

        assertThat(service.restore(command)).isTrue();

        verify(referenceDataService).requireActiveCode("AUCG01", AuctionStatusCode.ACTIVE);
        verify(auctionMapper).restoreAuctionAfterSanction(
                81L, AuctionStatusCode.ACTIVE, null, 7200L, "10");
    }
}
