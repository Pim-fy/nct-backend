package nct.ops.sanction.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import nct.ops.sanction.service.ReportEnforcementService;
import nct.ops.sanction.service.ReportSanctionService;

/** 담당자 7 - F-OPS-007: 만료된 7일 신고 제재를 작은 배치로 자동 해제합니다. */
@Component
@RequiredArgsConstructor
public class ReportSanctionExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReportSanctionExpiryScheduler.class);
    private static final int BATCH_SIZE = 50;

    private final ReportSanctionService reportSanctionService;
    private final ReportEnforcementService reportEnforcementService;

    @Scheduled(fixedDelayString = "${report-sanction.expiry.fixed-delay-ms:60000}")
    public void releaseExpiredSanctions() {
        for (Long sanctionSn : reportSanctionService.findExpiredUnprocessedIds(BATCH_SIZE)) {
            try {
                reportEnforcementService.releaseExpired(sanctionSn);
            } catch (RuntimeException exception) {
                log.error("Failed to release expired report sanction. sanctionSn={}", sanctionSn, exception);
            }
        }
    }
}
