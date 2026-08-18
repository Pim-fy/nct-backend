package nct.ops.risk.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 담당자 7 · REQ-OPS-011: 장기 보류 정산을 주기적으로 위험 이벤트로 전환합니다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementRiskDetectionScheduler {

    private final RiskDetectionService riskDetectionService;

    @Scheduled(fixedDelayString = "${risk.detection.settlement-scan-delay-ms:3600000}")
    public void scan() {
        try {
            riskDetectionService.scanReportSignals();
        } catch (RuntimeException exception) {
            log.warn("신고 리스크 주기 재집계에 실패했습니다.", exception);
        }
        try {
            riskDetectionService.scanLongHeldSettlements();
        } catch (RuntimeException exception) {
            log.warn("장기 정산 보류 리스크 집계에 실패했습니다.", exception);
        }
    }
}
