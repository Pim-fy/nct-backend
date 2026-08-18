package nct.ops.risk.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import nct.ops.risk.event.ReportCreatedRiskSignal;

/** 담당자 7 · REQ-OPS-011: 신고 저장 성공 뒤에만 리스크 판정을 실행합니다. */
@Component
@RequiredArgsConstructor
public class ReportRiskSignalListener {

    private final RiskDetectionService riskDetectionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(ReportCreatedRiskSignal signal) {
        riskDetectionService.evaluateReportSignals(signal);
    }
}
