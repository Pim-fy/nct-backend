package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import nct.auction.service.AuctionActivationScheduler;
import nct.auction.service.AuctionService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class AuctionActivationSchedulerTest {

    @Mock
    private AuctionService auctionService;

    @InjectMocks
    private AuctionActivationScheduler scheduler;

    @Test
    void activationSchedulerIsEnabledWhenPropertyIsMissing() {
        ConditionalOnProperty condition = AuctionActivationScheduler.class
                .getAnnotation(ConditionalOnProperty.class);

        assertThat(condition.matchIfMissing()).isTrue();
    }

    @Test
    void activatesEveryDueReadyAuction() {
        when(auctionService.findReadyAuctionIds(100)).thenReturn(List.of(10L, 20L));

        scheduler.activateReadyAuctions();

        verify(auctionService).activateReadyAuction(10L);
        verify(auctionService).activateReadyAuction(20L);
    }

    @Test
    void continuesWithNextAuctionWhenOneActivationFails() {
        when(auctionService.findReadyAuctionIds(100)).thenReturn(List.of(10L, 20L));
        doThrow(new CustomException(ErrorCode.INTERNAL_SERVER_ERROR))
                .when(auctionService)
                .activateReadyAuction(10L);

        scheduler.activateReadyAuctions();

        verify(auctionService).activateReadyAuction(10L);
        verify(auctionService).activateReadyAuction(20L);
    }
}
