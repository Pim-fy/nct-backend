package nct.trade.port;

/** 담당자 7 - F-OPS-007: 영구정지 시 서비스 거래와 보관금을 안전하게 취소하는 명령입니다. */
public record AdminServiceTradeCancellationCommand(
        Long tradeSn,
        Long adminUserSn,
        String reason,
        Long sourceReportSn) {
}
