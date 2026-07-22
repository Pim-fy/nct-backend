package nct.notification.service;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import nct.notification.dto.NotificationResponse;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Claude Code 작성 (BJN, 2026-07-22)
 *
 * DB·Spring 컨텍스트 없이 순수 Reactor로 구독/발행 계약만 검증한다.
 */
class NotificationEventBrokerTest {

    private final NotificationEventBroker broker = new NotificationEventBroker();

    @Test
    void publishReachesSubscriber() {
        NotificationResponse dto = NotificationResponse.builder().id(1L).title("t").build();

        // 하트비트(comment-only, event 이름 없음)는 걸러내고 named event만 본다
        Flux<String> events = broker.subscribe(1L)
                .filter(e -> e.event() != null)
                .map(e -> e.event());

        StepVerifier.create(events)
                .then(() -> broker.publish(1L, dto))
                .expectNext("notification")
                .thenCancel()
                .verify(Duration.ofSeconds(3));
    }

    @Test
    void publishToUnsubscribedUserIsNoop() {
        NotificationResponse dto = NotificationResponse.builder().id(1L).title("t").build();

        // 구독자가 없는 유저에게 publish해도 예외 없이 조용히 무시돼야 한다
        broker.publish(999L, dto);
    }
}
