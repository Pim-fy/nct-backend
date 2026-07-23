package nct.notification.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import nct.notification.dto.NotificationResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Claude Code 작성 (BJN, 2026-07-22)
 *
 * [알림 - 실시간 push 브로커] (SSE, WebFlux Flux/Sinks 기반)
 * - 유저별 멀티캐스트 싱크를 인메모리로 관리한다. 같은 유저가 여러 탭을 열어도 한 싱크에
 *   구독자만 여러 명 붙는 구조라 유저당 연결 목록을 따로 들고 있을 필요가 없다.
 * - 단일 인스턴스 배포를 전제로 한다 — 인스턴스를 늘리면 알림을 만든 인스턴스와 SSE가 붙은
 *   인스턴스가 다를 때 push가 조용히 유실된다. 필요해지면 Redis Pub/Sub 등으로 확장할 지점.
 */
@Slf4j
@Component
public class NotificationEventBroker {

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(20);

    private final Map<Long, Channel> channels = new ConcurrentHashMap<>();

    /** 알림 스트림 구독 — usrSn별 싱크를 공유하고, 하트비트를 merge해 idle 연결이 프록시에 끊기는 걸 막는다 */
    public Flux<ServerSentEvent<NotificationResponse>> subscribe(long usrSn) {
        Channel channel = channels.computeIfAbsent(usrSn, k -> new Channel());
        channel.subscriberCount.incrementAndGet();

        Flux<ServerSentEvent<NotificationResponse>> heartbeat = Flux.interval(HEARTBEAT_INTERVAL)
                .map(tick -> ServerSentEvent.<NotificationResponse>builder().comment("ping").build());

        // 구독 취소(탭 닫힘·재연결)를 감지해 마지막 구독자가 빠지면 채널을 정리한다 (누수 방지)
        return Flux.merge(channel.sink.asFlux(), heartbeat)
                .doFinally(signal -> {
                    if (channel.subscriberCount.decrementAndGet() <= 0) {
                        channels.remove(usrSn, channel);
                    }
                });
    }

    /** 알림 push — 구독 중인 유저가 없으면 조용히 무시한다 (정상 상황) */
    public void publish(long usrSn, NotificationResponse dto) {
        Channel channel = channels.get(usrSn);
        if (channel == null) {
            return;
        }
        ServerSentEvent<NotificationResponse> event =
                ServerSentEvent.builder(dto).event("notification").build();
        Sinks.EmitResult result = channel.sink.tryEmitNext(event);
        if (result.isFailure()) {
            log.warn("알림 SSE push 실패 (usrSn={}, result={})", usrSn, result);
        }
    }

    /** usrSn 하나에 대응하는 싱크 + 구독자 수 — 두 값을 항상 같이 관리하기 위해 한 단위로 묶었다 */
    private static final class Channel {
        private final Sinks.Many<ServerSentEvent<NotificationResponse>> sink =
                Sinks.many().multicast().onBackpressureBuffer();
        private final AtomicInteger subscriberCount = new AtomicInteger();
    }
}
