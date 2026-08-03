package nct.trade.port;

/**
 * 담당자6 보관금 도메인 어댑터 계약이다.
 * 호출 실패는 상위 거래 생성 트랜잭션을 롤백해야 하며, 참조 기준은 RefType.TRADE + tradeId다.
 */
public interface ServiceEscrowCreator {

    void createEscrow(ServiceEscrowCreateCommand command);
}
