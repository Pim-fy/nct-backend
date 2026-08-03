package nct.trade.dto;

/** 서비스 일정 취소 요청의 서버 내부 명령이다. */
public record ServiceScheduleCancellationCommand(String reason) {
}
