package nct.settlement.port;

import java.util.List;
import java.util.Map;

import nct.settlement.dto.AdminSettlementSummary;

/** 거래별 정산 원본 상태를 정산 소유 경계에서 일괄 제공하는 읽기 계약입니다. */
public interface AdminSettlementSummaryReader {

    Map<Long, AdminSettlementSummary> findSummaries(List<Long> tradeIds);
}
