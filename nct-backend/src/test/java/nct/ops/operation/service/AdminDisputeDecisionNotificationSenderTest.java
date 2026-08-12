package nct.ops.operation.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import nct.notification.service.NotificationService;

/** 담당자 7 · F-OPS-006: 수신자별 판정 알림 위임을 검증합니다. */
class AdminDisputeDecisionNotificationSenderTest {

    @Test
    void delegatesResolvedNotification() {
        NotificationService notificationService = mock(NotificationService.class);
        var sender = new AdminDisputeDecisionNotificationSender(notificationService);

        sender.send(32L, 11L, "전액 환불");

        verify(notificationService).notifyTradeReportResolved(32L, 11L, "전액 환불");
    }
}
