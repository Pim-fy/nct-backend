package nct.trade.port;

import java.util.Collection;
import java.util.Map;

import nct.trade.dto.AdminReportTradeReference;

/** 거래 소유 영역이 관리자 신고에 상품·서비스 요청 연결 정보만 제공하는 읽기 계약입니다. */
public interface AdminReportTradeReferenceReader {

    Map<Long, AdminReportTradeReference> findByTradeSns(Collection<Long> tradeSns);
}
