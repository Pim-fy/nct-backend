package nct.trade.dto;

import java.time.LocalDateTime;

/** 서비스 일정 변경 요청의 서버 내부 명령이다. */
public record ServiceScheduleChangeCommand(
        LocalDateTime requestedScheduleAt,
        String reason) {
}
