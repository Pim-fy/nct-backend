package nct.trade.dto;

import java.time.LocalDateTime;

/** 서비스 거래 상세 화면에 표시할 일정 변경·취소 이력 한 건이다. */
public record ServiceScheduleHistoryItem(
        long id,
        String eventType,
        LocalDateTime occurredAt,
        LocalDateTime requestedScheduleAt,
        String reason) {
}
