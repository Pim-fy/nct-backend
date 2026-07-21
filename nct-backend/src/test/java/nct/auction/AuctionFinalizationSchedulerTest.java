package nct.auction;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.auction.service.AuctionFinalizationScheduler;
import nct.auction.service.AuctionService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class AuctionFinalizationSchedulerTest {

    @Mock
    private AuctionService auctionService;

    @InjectMocks
    private AuctionFinalizationScheduler scheduler;

    @Test
    void continuesWithNextAuctionWhenOneFinalizationFails() {
        when(auctionService.findExpiredActiveAuctionIds(100)).thenReturn(List.of(10L, 20L));
        doThrow(new CustomException(ErrorCode.BUYER_ADDRESS_INCOMPLETE))
                .when(auctionService)
                .finalizeExpiredAuction(10L);

        scheduler.finalizeExpiredAuctions();

        verify(auctionService).finalizeExpiredAuction(10L);
        verify(auctionService).finalizeExpiredAuction(20L);
    }

    @Test
    void retriesStillActiveExpiredAuctionOnNextRun() {
        when(auctionService.findExpiredActiveAuctionIds(100)).thenReturn(List.of(10L));
        doThrow(new CustomException(ErrorCode.BUYER_ADDRESS_INCOMPLETE))
                .when(auctionService)
                .finalizeExpiredAuction(10L);

        scheduler.finalizeExpiredAuctions();
        scheduler.finalizeExpiredAuctions();

        verify(auctionService, times(2)).findExpiredActiveAuctionIds(100);
        verify(auctionService, times(2)).finalizeExpiredAuction(10L);
    }
}
