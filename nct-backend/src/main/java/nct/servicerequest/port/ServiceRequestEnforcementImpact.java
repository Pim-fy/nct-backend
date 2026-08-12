package nct.servicerequest.port;

import java.time.LocalDateTime;

/** 담당자 7 · 신고 제재: 요청서별 적용 결과와 복구할 남은 마감시간입니다. */
public record ServiceRequestEnforcementImpact(
        Long serviceRequestId,
        String actionCode,
        String previousStatusCode,
        LocalDateTime previousDeadlineAt,
        Long remainingSeconds,
        String result) {
}
