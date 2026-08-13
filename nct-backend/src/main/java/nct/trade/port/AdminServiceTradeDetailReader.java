package nct.trade.port;

import nct.trade.dto.ServiceTradeDetailResponse;

/** 담당자 7 · F-OPS-005: 운영 화면이 서비스 거래의 읽기 전용 상세를 조회하는 계약입니다. */
public interface AdminServiceTradeDetailReader {

    ServiceTradeDetailResponse findByTradeId(long tradeId);
}
