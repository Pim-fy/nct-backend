package nct.trade.port;

/** 담당자6 보관금 생성에 전달할 서버 확정 서비스 거래 참조값이다. */
public record ServiceEscrowCreateCommand(
        long tradeId,
        long requesterUserId,
        long amount) {
}
