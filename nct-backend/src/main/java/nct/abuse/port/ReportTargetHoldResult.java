package nct.abuse.port;

import java.time.LocalDateTime;

/** 담당자 7 · F-OPS-007: 신고 보류 결과와 복구에 필요한 기준 상태입니다. */
public record ReportTargetHoldResult(
        Long referenceSn,
        boolean changed,
        boolean alreadyOnReportHold,
        String previousStatusCode,
        LocalDateTime previousStartAt,
        LocalDateTime previousDeadlineAt,
        Long remainingStartSeconds,
        Long remainingSeconds,
        boolean settlementHoldApplied,
        String result) {
}
