package nct.trade.dto;

import java.time.LocalDateTime;

/** 일정 변경·취소 흐름이 확정된 뒤 상세 화면에 표시할 이력 한 건의 공통 형태다. */
public record ServiceScheduleHistoryItem(
        long id,
        String eventType,
        String status,
        long requesterUserId,
        LocalDateTime requestedAt,
        LocalDateTime requestedScheduleAt,
        String reason) {
}
