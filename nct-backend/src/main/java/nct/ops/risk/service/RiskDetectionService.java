package nct.ops.risk.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.common.domain.RefType;
import nct.ops.risk.event.ReportCreatedRiskSignal;
import nct.ops.risk.port.ReportRiskSignalReader;
import nct.ops.risk.port.RiskDetectionPolicy;
import nct.ops.risk.port.RiskDetectionPolicyReader;
import nct.ops.risk.port.SettlementRiskCandidate;
import nct.ops.risk.port.SettlementRiskSignalReader;

/** 담당자 7 · REQ-OPS-011: 원천 통계가 설정 기준을 넘으면 공통 RISK_EVENT를 생성합니다. */
@Service
@RequiredArgsConstructor
public class RiskDetectionService {

    private static final int SCAN_BATCH_SIZE = 200;

    static final String TRADE_REPORT_SURGE = "RSKC0005";
    static final String LONG_HELD_SETTLEMENT = "RSKC0006";
    static final String REPEATED_REPORT = "RSKC0007";
    static final String ADMIN_LOGIN_FAILURE = "RSKC0008";

    private final RiskDetectionPolicyReader policyReader;
    private final ReportRiskSignalReader reportSignalReader;
    private final SettlementRiskSignalReader settlementSignalReader;
    private final RiskEventService riskEventService;

    @Transactional
    public void evaluateReportSignals(ReportCreatedRiskSignal signal) {
        RiskDetectionPolicy policy = policyReader.getPolicy();
        LocalDateTime now = LocalDateTime.now();

        if (signal.tradeReport()) {
            LocalDateTime since = now.minusMinutes(policy.tradeReportWindowMinutes());
            long count = reportSignalReader.countTradeReportsSince(since);
            if (count >= policy.tradeReportCount()) {
                riskEventService.recordOnceSince(new RiskEventCommand(
                        TRADE_REPORT_SURGE,
                        null,
                        null,
                        String.format("최근 %d분 거래 신고 %d건 이상 감지",
                                policy.tradeReportWindowMinutes(), policy.tradeReportCount()),
                        "SYSTEM"), since);
            }
        }

        if (signal.reportedUserSn() != null && signal.reportedUserSn() > 0) {
            LocalDateTime since = now.minusDays(policy.repeatReportWindowDays());
            long reporterCount = reportSignalReader.countDistinctReportersForTargetSince(
                    signal.reportedUserSn(), since);
            if (reporterCount >= policy.repeatReportCount()) {
                riskEventService.recordOnceSince(new RiskEventCommand(
                        REPEATED_REPORT,
                        RefType.MEMBER.getCode(),
                        signal.reportedUserSn(),
                        String.format("최근 %d일 서로 다른 신고자 %d명 이상 감지",
                                policy.repeatReportWindowDays(), policy.repeatReportCount()),
                        "SYSTEM"), since);
            }
        }
    }

    @Transactional
    public int scanLongHeldSettlements() {
        RiskDetectionPolicy policy = policyReader.getPolicy();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(policy.settlementHoldDays());
        int createdCount = 0;

        for (SettlementRiskCandidate candidate
                : settlementSignalReader.findLongHeldSettlements(cutoff, SCAN_BATCH_SIZE)) {
            if (riskEventService.recordOnceSince(new RiskEventCommand(
                    LONG_HELD_SETTLEMENT,
                    RefType.SETTLEMENT.getCode(),
                    candidate.settlementSn(),
                    String.format("정산 보류 %d일 초과 감지 · 거래 %d",
                            policy.settlementHoldDays(), candidate.tradeSn()),
                    "SYSTEM"), candidate.holdStartedAt()).created()) {
                createdCount++;
            }
        }
        return createdCount;
    }

    /** 담당자 7 · REQ-OPS-011: AFTER_COMMIT 일시 실패를 주기 재집계로 복구합니다. */
    @Transactional
    public int scanReportSignals() {
        RiskDetectionPolicy policy = policyReader.getPolicy();
        LocalDateTime now = LocalDateTime.now();
        int createdCount = 0;

        LocalDateTime tradeSince = now.minusMinutes(policy.tradeReportWindowMinutes());
        if (reportSignalReader.countTradeReportsSince(tradeSince) >= policy.tradeReportCount()
                && riskEventService.recordOnceSince(new RiskEventCommand(
                        TRADE_REPORT_SURGE,
                        null,
                        null,
                        String.format("최근 %d분 거래 신고 %d건 이상 감지",
                                policy.tradeReportWindowMinutes(), policy.tradeReportCount()),
                        "SYSTEM"), tradeSince).created()) {
            createdCount++;
        }

        LocalDateTime repeatSince = now.minusDays(policy.repeatReportWindowDays());
        for (Long reportedUserSn : reportSignalReader.findRepeatedReportedUserIdsSince(
                repeatSince, policy.repeatReportCount(), SCAN_BATCH_SIZE)) {
            if (riskEventService.recordOnceSince(new RiskEventCommand(
                    REPEATED_REPORT,
                    RefType.MEMBER.getCode(),
                    reportedUserSn,
                    String.format("최근 %d일 서로 다른 신고자 %d명 이상 감지",
                            policy.repeatReportWindowDays(), policy.repeatReportCount()),
                    "SYSTEM"), repeatSince).created()) {
                createdCount++;
            }
        }
        return createdCount;
    }
}
