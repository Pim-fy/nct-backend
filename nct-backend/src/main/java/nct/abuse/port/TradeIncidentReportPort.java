package nct.abuse.port;

/** 담당자 7 · F-OPS-007: 거래 문제 접수를 신고 사건의 하위 처리정보로 연결하는 계약입니다. */
public interface TradeIncidentReportPort {

    Long create(TradeIncidentReportCommand command);
}
