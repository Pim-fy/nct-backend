package nct.ops.operation.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nct.ops.operation.domain.AdminDisputeDecisionCommittedEvent;

/** 담당자 7 · F-OPS-005/006: 판정 커밋 후에만 거래 신고 결과 알림과 이메일을 발행합니다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminDisputeDecisionNotificationListener {

    private final AdminDisputeDecisionNotificationSender notificationSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyParticipants(AdminDisputeDecisionCommittedEvent event) {
        for (Long userSn : event.recipientUserSns()) {
            try {
                notificationSender.send(userSn, event.reportSn(), event.resultText());
            } catch (RuntimeException exception) {
                log.warn(
                        "거래 신고 판정 커밋 후 알림 발행 실패: reportSn={}, userSn={}, errorType={}",
                        event.reportSn(), userSn, exception.getClass().getSimpleName());
            }
        }
    }
}
