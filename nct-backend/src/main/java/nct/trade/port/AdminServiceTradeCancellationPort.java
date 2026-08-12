package nct.trade.port;

/** 담당자 7 - F-OPS-007: 거래 도메인이 소유하는 서비스 거래 취소·환불 계약입니다. */
public interface AdminServiceTradeCancellationPort {

    boolean cancel(AdminServiceTradeCancellationCommand command);
}
