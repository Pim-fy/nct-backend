package nct.servicerequest.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import nct.servicerequest.service.ServiceRequestService;

/**
 * 마감(SVCC0004, 수동 마감·기한만료 자동마감 공통)된 지 1일이 지난 요청서를 자동으로 소프트 삭제한다.
 * ProductCancelledAutoDeleteScheduler와 동일한 방식.
 */
@Slf4j
@Component
public class ServiceRequestClosedAutoDeleteScheduler {

    private static final int BATCH_SIZE = 100;
    private static final long EXPIRE_AFTER_DAYS = 1;

    private final ServiceRequestService serviceRequestService;
    private final boolean schedulerEnabled;

    public ServiceRequestClosedAutoDeleteScheduler(
            ServiceRequestService serviceRequestService,
            @Value("${service-request.closed-auto-delete.enabled:false}") boolean schedulerEnabled) {
        this.serviceRequestService = serviceRequestService;
        this.schedulerEnabled = schedulerEnabled;
    }

    @Scheduled(cron = "${service-request.closed-auto-delete.cron:0 0 * * * *}")
    public void deleteExpiredClosedServiceRequests() {
        if (!schedulerEnabled) {
            return;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(EXPIRE_AFTER_DAYS);
        List<Long> svcReqSns = serviceRequestService.findExpiredClosedServiceRequestIds(cutoff, BATCH_SIZE);

        for (Long svcReqSn : svcReqSns) {
            try {
                serviceRequestService.deleteExpiredClosedServiceRequest(svcReqSn);
            } catch (RuntimeException exception) {
                log.warn("마감된 요청서 자동 삭제에 실패했습니다. svcReqSn={}", svcReqSn, exception);
            }
        }
    }
}
