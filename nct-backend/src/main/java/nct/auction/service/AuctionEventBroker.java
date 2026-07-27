package nct.auction.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import nct.auction.dto.AuctionRealtimeEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Slf4j
@Component
public class AuctionEventBroker {

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(20);
    private static final String EVENT_NAME = "auction-updated";

    private final Map<Long, Channel> channels = new ConcurrentHashMap<>();

    public Flux<ServerSentEvent<AuctionRealtimeEvent>> subscribe(long auctionId) {
        Channel channel = channels.computeIfAbsent(auctionId, key -> new Channel());
        channel.subscriberCount.incrementAndGet();

        Flux<ServerSentEvent<AuctionRealtimeEvent>> heartbeat = Flux.interval(HEARTBEAT_INTERVAL)
                .map(tick -> ServerSentEvent.<AuctionRealtimeEvent>builder().comment("ping").build());

        return Flux.merge(channel.sink.asFlux(), heartbeat)
                .doFinally(signal -> {
                    if (channel.subscriberCount.decrementAndGet() <= 0) {
                        channels.remove(auctionId, channel);
                    }
                });
    }

    public void publish(AuctionRealtimeEvent event) {
        Channel channel = channels.get(event.getAuctionId());
        if (channel == null) {
            return;
        }

        ServerSentEvent<AuctionRealtimeEvent> serverEvent = ServerSentEvent.builder(event)
                .event(EVENT_NAME)
                .build();
        Sinks.EmitResult result = channel.sink.tryEmitNext(serverEvent);
        if (result.isFailure()) {
            log.warn(
                    "경매 SSE 전송 실패 (auctionId={}, result={})",
                    event.getAuctionId(),
                    result);
        }
    }

    private static final class Channel {
        private final Sinks.Many<ServerSentEvent<AuctionRealtimeEvent>> sink =
                Sinks.many().multicast().onBackpressureBuffer();
        private final AtomicInteger subscriberCount = new AtomicInteger();
    }
}
