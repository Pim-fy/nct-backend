package nct.auction.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;
import nct.auction.dto.AuctionRealtimeEvent;

@Component
@RequiredArgsConstructor
public class AuctionEventPublisher {

    private final AuctionEventBroker eventBroker;

    public void publishAfterCommit(AuctionRealtimeEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventBroker.publish(event);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventBroker.publish(event);
            }
        });
    }
}
