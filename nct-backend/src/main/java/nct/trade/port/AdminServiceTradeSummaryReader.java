package nct.trade.port;

import java.util.List;
import java.util.Map;

import nct.trade.dto.AdminServiceTradeSummary;

/** 서비스 요청별 거래·진행 중 분쟁 상태를 거래 소유 경계에서 일괄 제공하는 읽기 계약입니다. */
public interface AdminServiceTradeSummaryReader {

    Map<Long, AdminServiceTradeSummary> findSummaries(List<Long> serviceRequestIds);
}
