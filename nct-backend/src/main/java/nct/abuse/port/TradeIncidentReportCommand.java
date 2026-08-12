package nct.abuse.port;

import java.util.List;

/** 담당자 7 · F-OPS-007: 거래 도메인이 검증한 거래 문제를 상위 신고 사건으로 등록하는 명령입니다. */
public record TradeIncidentReportCommand(
        Long tradeSn,
        Long reporterUserSn,
        Long reportedUserSn,
        String reportTypeCode,
        String content,
        List<Long> fileSns,
        String previousTradeStatusCode,
        Long remainingAutoCompleteSeconds,
        boolean settlementHoldApplied,
        boolean chatClosed) {
}
