package nct.trade.dto;

/** 거래 행 잠금 뒤 조회하는 미처리 서비스 일정 취소 요청이다. */
public record ServiceScheduleCancellationPending(long historyId, long requesterUserId) {
}
