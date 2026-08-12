package nct.abuse.port;

/** 담당자 7 - F-OPS-007: 거래 취소 전에 다른 미처리 신고가 있는지 확인하는 읽기 계약입니다. */
public interface ActiveAbuseReportReferenceReader {

    boolean hasOtherActiveReportLinkedToTrade(Long tradeSn, Long excludedReportSn);

    boolean hasOtherActiveReportLinkedToAuction(Long auctionSn, Long excludedReportSn);
}
