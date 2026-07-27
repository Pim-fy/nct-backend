package nct.auction;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import nct.auction.dto.AuctionRealtimeEvent;
import nct.auction.service.AuctionEventBroker;
import nct.auction.service.AuctionEventPublisher;

class AuctionEventPublisherTest {

    private final AuctionEventBroker eventBroker = mock(AuctionEventBroker.class);
    private final AuctionEventPublisher eventPublisher = new AuctionEventPublisher(eventBroker);

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishesImmediatelyWithoutTransaction() {
        AuctionRealtimeEvent event = new AuctionRealtimeEvent(10L, "BID_PLACED");

        eventPublisher.publishAfterCommit(event);

        verify(eventBroker).publish(event);
    }

    @Test
    void publishesOnlyAfterCommitWhenTransactionIsActive() {
        AuctionRealtimeEvent event = new AuctionRealtimeEvent(10L, "BID_PLACED");
        TransactionSynchronizationManager.initSynchronization();

        eventPublisher.publishAfterCommit(event);
        verifyNoInteractions(eventBroker);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(eventBroker).publish(event);
    }

    @Test
    void doesNotPublishWhenTransactionRollsBack() {
        AuctionRealtimeEvent event = new AuctionRealtimeEvent(10L, "BID_PLACED");
        TransactionSynchronizationManager.initSynchronization();

        eventPublisher.publishAfterCommit(event);

        verifyNoInteractions(eventBroker);
    }
}
