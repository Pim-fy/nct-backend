package nct.notification.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nct.notification.dto.NotificationResponse;

/**
 * Claude Code 작성 (BJN, 2026-07-22)
 *
 * [알림 - 실시간 push 발행 시점 결정]
 * - notify()/notifyImportant()는 대개 더 큰 비즈니스 트랜잭션(예: PointService.refundEscrow)
 *   안에서 호출된다. 그 트랜잭션이 롤백되면 "실제로는 없었던 일"에 대해 push가 나가면 안 되므로,
 *   활성 트랜잭션이 있으면 커밋 이후로 push를 미룬다.
 * - 다만 일부 notify* 편의 메서드는 자체 @Transactional이 없고 호출자 트랜잭션에 얹혀갈
 *   뿐이라, 활성 트랜잭션이 아예 없는 상태로 불릴 수도 있다 — 그때는 즉시 push한다. 트랜잭션이
 *   없다는 건 MySQL autocommit으로 insert가 이미 커밋된 상태라는 뜻이라 즉시 push해도 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventPublisher {

    private final NotificationEventBroker broker;

    /** usrSn을 dto와 별도로 받는다 — NotificationResponse는 REST 응답 DTO라 usrSn 필드가 없다 */
    public void publishAfterCommit(long usrSn, NotificationResponse dto) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.debug("알림 push: 활성 트랜잭션 없이 호출됨 — 즉시 push (usrSn={})", usrSn);
            broker.publish(usrSn, dto);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                broker.publish(usrSn, dto);
            }
        });
    }
}
