package nct.ops.operation.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.Set;

import org.junit.jupiter.api.Test;

import nct.ops.operation.domain.AdminDisputeDecisionCommittedEvent;

/** 담당자 7 · F-OPS-006: 커밋 후 최종 판정 알림 수신자 조립을 검증합니다. */
class AdminDisputeDecisionNotificationListenerTest {

    @Test
    void isolatesParticipantNotificationFailures() {
        AdminDisputeDecisionNotificationSender notificationSender =
                mock(AdminDisputeDecisionNotificationSender.class);
        var listener = new AdminDisputeDecisionNotificationListener(notificationSender);
        doThrow(new IllegalStateException("첫 수신자 알림 실패"))
                .when(notificationSender).send(32L, 11L, "전액 환불");

        listener.notifyParticipants(new AdminDisputeDecisionCommittedEvent(
                Set.of(32L, 33L), 11L, "전액 환불"));

        verify(notificationSender).send(32L, 11L, "전액 환불");
        verify(notificationSender).send(33L, 11L, "전액 환불");
    }
}
