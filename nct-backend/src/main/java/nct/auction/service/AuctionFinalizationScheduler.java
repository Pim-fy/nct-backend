package nct.auction.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "auction.finalization",
        name = "scheduler-enabled",
        havingValue = "true")
public class AuctionFinalizationScheduler {

    private static final int BATCH_SIZE = 100;

    private final AuctionService auctionService;

    @Scheduled(fixedDelayString = "${auction.finalization.fixed-delay-ms:60000}")
    public void finalizeExpiredAuctions() {
        for (Long auctionId : auctionService.findExpiredActiveAuctionIds(BATCH_SIZE)) {
            try {
                auctionService.finalizeExpiredAuction(auctionId);
            } catch (RuntimeException exception) {
                log.error("Failed to finalize expired auction. auctionId={}", auctionId, exception);
            }
        }
    }
}
