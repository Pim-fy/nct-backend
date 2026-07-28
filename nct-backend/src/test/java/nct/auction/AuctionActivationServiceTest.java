package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.auction.dto.AuctionRealtimeEvent;
import nct.auction.mapper.AuctionMapper;
import nct.auction.service.AuctionEventPublisher;
import nct.auction.service.AuctionService;

@ExtendWith(MockitoExtension.class)
class AuctionActivationServiceTest {

    @Mock
    private AuctionMapper auctionMapper;

    @Mock
    private AuctionEventPublisher auctionEventPublisher;

    @InjectMocks
    private AuctionService auctionService;

    @Test
    void activatesDueReadyAuctionAndPublishesRealtimeEvent() {
        when(auctionMapper.activateReadyAuction(10L, "SYSTEM")).thenReturn(1);

        assertThat(auctionService.activateReadyAuction(10L)).isTrue();

        ArgumentCaptor<AuctionRealtimeEvent> eventCaptor =
                ArgumentCaptor.forClass(AuctionRealtimeEvent.class);
        verify(auctionEventPublisher).publishAfterCommit(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getAuctionId()).isEqualTo(10L);
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("AUCTION_ACTIVATED");
    }

    @Test
    void doesNotPublishWhenAuctionIsNoLongerReady() {
        when(auctionMapper.activateReadyAuction(10L, "SYSTEM")).thenReturn(0);

        assertThat(auctionService.activateReadyAuction(10L)).isFalse();

        verify(auctionEventPublisher, never()).publishAfterCommit(
                org.mockito.ArgumentMatchers.any(AuctionRealtimeEvent.class));
    }
}
