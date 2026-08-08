package nct.point.port;

import java.util.List;
import java.util.Map;

import nct.point.dto.AdminEscrowSummary;

/** 거래 참조별 보관금·정산 원장 합계를 포인트 소유 경계에서 일괄 제공하는 읽기 계약입니다. */
public interface AdminEscrowSummaryReader {

    Map<Long, AdminEscrowSummary> findSummaries(List<Long> tradeIds);
}
