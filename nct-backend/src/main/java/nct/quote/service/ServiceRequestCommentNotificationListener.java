package nct.quote.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nct.common.domain.RefType;
import nct.notification.domain.NotificationDomain;
import nct.notification.domain.NotificationType;
import nct.notification.service.NotificationService;
import nct.quote.port.ServiceRequestQuoteProviderReader;
import nct.servicerequest.domain.ServiceRequestCommentAddedEvent;

/**
 * 담당자2 · F-SVC-006: 요청서 변경사항 추가 커밋 후 견적 제출 제공자 알림.
 * servicerequest 도메인이 quote 도메인을 직접 참조하면 QuoteService와 순환참조가 되므로
 * 이벤트로 발행해 받는다(AdminDisputeDecisionNotificationListener와 동일한 패턴).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceRequestCommentNotificationListener {

    private final ServiceRequestQuoteProviderReader serviceRequestQuoteProviderReader;
    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyProviders(ServiceRequestCommentAddedEvent event) {
        List<Long> providerUsrSns = serviceRequestQuoteProviderReader.findProviderUsrSnBySvcReqSn(event.svcReqSn());
        for (Long providerUsrSn : providerUsrSns) {
            try {
                sendNotification(providerUsrSn, event);
            } catch (RuntimeException exception) {
                log.warn(
                        "요청서 변경사항 알림 발행 실패: svcReqSn={}, providerUsrSn={}, errorType={}",
                        event.svcReqSn(), providerUsrSn, exception.getClass().getSimpleName());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendNotification(Long providerUsrSn, ServiceRequestCommentAddedEvent event) {
        notificationService.notify(
                providerUsrSn,
                NotificationType.SERVICE,
                NotificationDomain.SERVICE,
                "견적 제출한 요청서에 변경사항이 추가되었습니다",
                event.svcReqTtl() + " - " + event.changeTitle(),
                RefType.SERVICE_REQUEST,
                event.svcReqSn());
    }
}
