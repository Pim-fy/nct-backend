package nct.ops.operation.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.notification.service.NotificationService;

/** 담당자 7 · F-OPS-006: 판정 알림을 수신자별 독립 트랜잭션으로 저장·발송합니다. */
@Service
@RequiredArgsConstructor
public class AdminDisputeDecisionNotificationSender {

    private final NotificationService notificationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(Long userSn, Long reportSn, String resultText) {
        notificationService.notifyTradeReportResolved(userSn, reportSn, resultText);
    }
}
