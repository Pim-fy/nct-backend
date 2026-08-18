package nct.abuse.port;

/** 담당자 7 · F-OPS-010: 관리자 신고 목록과 같은 기준의 활성 거래 분쟁 건수를 제공합니다. */
public interface AdminActiveDisputeCountReader {

    long countActiveDisputesForAdmin();
}
