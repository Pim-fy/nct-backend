package nct.notification.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import nct.notification.dto.NotificationResponse;

/**
 * Claude Code 작성 (BJN, 2026-07-22)
 *
 * DB·Spring 컨텍스트·공유 DB 커밋 없이 TransactionSynchronizationManager를 직접 제어해서
 * "활성 트랜잭션 + 커밋", "활성 트랜잭션 + 롤백(=afterCommit 미실행)", "트랜잭션 없음" 세 경로를
 * 순수 단위 테스트로 검증한다.
 */
class NotificationEventPublisherTest {

    private final NotificationEventBroker broker = mock(NotificationEventBroker.class);
    private final NotificationEventPublisher publisher = new NotificationEventPublisher(broker);

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishesImmediatelyWhenNoActiveTransaction() {
        NotificationResponse dto = NotificationResponse.builder().id(1L).build();

        publisher.publishAfterCommit(1L, dto);

        verify(broker).publish(1L, dto);
    }

    @Test
    void publishesAfterCommitWhenTransactionActive() {
        NotificationResponse dto = NotificationResponse.builder().id(1L).build();
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishAfterCommit(1L, dto);
        verifyNoInteractions(broker); // 커밋 전이라 아직 안 나가야 한다

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(broker).publish(1L, dto);
    }

    @Test
    void doesNotPublishWhenTransactionRolledBack() {
        NotificationResponse dto = NotificationResponse.builder().id(1L).build();
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishAfterCommit(1L, dto);
        // afterCommit()을 호출하지 않고 그대로 종료 = 롤백 상황을 흉내

        verifyNoInteractions(broker);
    }
}
