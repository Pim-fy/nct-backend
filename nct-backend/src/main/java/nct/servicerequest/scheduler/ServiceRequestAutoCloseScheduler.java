package nct.servicerequest.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nct.servicerequest.service.ServiceRequestService;

/**
 * 견적 요청 기간(공개 후 5일, 희망일이 더 빠르면 희망일)이 지난 공개 요청서를 주기적으로 마감 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "service-request.auto-close",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ServiceRequestAutoCloseScheduler {

    private static final int BATCH_SIZE = 100;

    private final ServiceRequestService serviceRequestService;

    @Scheduled(fixedDelayString = "${service-request.auto-close.fixed-delay-ms:60000}")
    public void closeExpiredServiceRequests() {
        for (Long svcReqSn : serviceRequestService.findExpiredOpenServiceRequestIds(BATCH_SIZE)) {
            try {
                serviceRequestService.autoCloseExpiredServiceRequest(svcReqSn);
            } catch (RuntimeException exception) {
                log.warn("서비스 요청 자동 마감 처리에 실패했습니다. svcReqSn={}", svcReqSn, exception);
            }
        }
    }
}
