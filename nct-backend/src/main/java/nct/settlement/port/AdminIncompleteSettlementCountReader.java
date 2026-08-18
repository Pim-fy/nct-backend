package nct.settlement.port;

/** 담당자 7 · F-OPS-010: 관리자 정산 목록과 같은 기준의 미완료 정산 건수를 제공합니다. */
public interface AdminIncompleteSettlementCountReader {

    long countIncompleteSettlementsForAdmin();
}
