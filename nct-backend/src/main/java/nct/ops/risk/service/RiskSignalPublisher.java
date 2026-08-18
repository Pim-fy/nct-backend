package nct.ops.risk.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import nct.ops.risk.event.ReportCreatedRiskSignal;

/** 담당자 7 · REQ-OPS-011: 원천 업무가 RISK_EVENT 테이블을 직접 쓰지 않게 합니다. */
@Component
@RequiredArgsConstructor
public class RiskSignalPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void reportCreated(long reportSn, Long reportedUserSn, boolean tradeReport) {
        eventPublisher.publishEvent(new ReportCreatedRiskSignal(reportSn, reportedUserSn, tradeReport));
    }
}
