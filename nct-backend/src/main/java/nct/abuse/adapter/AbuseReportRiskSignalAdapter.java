package nct.abuse.adapter;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import nct.abuse.mapper.AbuseReportMapper;
import nct.ops.risk.port.ReportRiskSignalReader;

/** 담당자 7 · REQ-OPS-011: 신고 통계만 제공하고 RISK_EVENT 저장은 운영 계약에 위임합니다. */
@Component
@RequiredArgsConstructor
public class AbuseReportRiskSignalAdapter implements ReportRiskSignalReader {

    private final AbuseReportMapper abuseReportMapper;

    @Override
    public long countTradeReportsSince(LocalDateTime since) {
        return abuseReportMapper.countTradeReportsSince(since);
    }

    @Override
    public long countDistinctReportersForTargetSince(long reportedUserSn, LocalDateTime since) {
        return abuseReportMapper.countDistinctReportersForTargetSince(reportedUserSn, since);
    }

    @Override
    public List<Long> findRepeatedReportedUserIdsSince(
            LocalDateTime since,
            int minimumReporterCount,
            int limit) {
        return abuseReportMapper.findRepeatedReportedUserIdsSince(
                since, minimumReporterCount, limit);
    }
}
