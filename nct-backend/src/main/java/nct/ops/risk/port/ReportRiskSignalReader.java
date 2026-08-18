package nct.ops.risk.port;

import java.time.LocalDateTime;
import java.util.List;

/** 담당자 7 · REQ-OPS-011: 신고 소유 영역이 제공하는 리스크 집계 읽기 계약입니다. */
public interface ReportRiskSignalReader {

    long countTradeReportsSince(LocalDateTime since);

    long countDistinctReportersForTargetSince(long reportedUserSn, LocalDateTime since);

    List<Long> findRepeatedReportedUserIdsSince(
            LocalDateTime since,
            int minimumReporterCount,
            int limit);
}
