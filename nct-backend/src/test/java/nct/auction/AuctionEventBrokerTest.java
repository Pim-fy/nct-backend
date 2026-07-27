package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import nct.auction.dto.AuctionRealtimeEvent;
import nct.auction.service.AuctionEventBroker;
import reactor.test.StepVerifier;

class AuctionEventBrokerTest {

    private final AuctionEventBroker eventBroker = new AuctionEventBroker();

    @Test
    void publishesOnlyToSubscribersOfTheChangedAuction() {
        AuctionRealtimeEvent event = new AuctionRealtimeEvent(10L, "BID_PLACED");

        StepVerifier.create(eventBroker.subscribe(10L))
                .then(() -> {
                    eventBroker.publish(new AuctionRealtimeEvent(20L, "BID_PLACED"));
                    eventBroker.publish(event);
                })
                .assertNext(serverEvent -> {
                    assertThat(serverEvent.event()).isEqualTo("auction-updated");
                    assertThat(serverEvent.data()).isSameAs(event);
                })
                .thenCancel()
                .verify(Duration.ofSeconds(1));
    }
}
