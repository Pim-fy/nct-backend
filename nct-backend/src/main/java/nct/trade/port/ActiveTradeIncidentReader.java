package nct.trade.port;

/** 담당자 7 · F-OPS-007: 현재 신고를 제외한 거래의 미해결 신고·하위 분쟁 존재 여부를 조회합니다. */
public interface ActiveTradeIncidentReader {

    boolean hasOtherOpenIncident(Long tradeSn, Long excludedReportSn);
}
