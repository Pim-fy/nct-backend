package nct.quote.port;

import java.util.List;
import java.util.Map;

import nct.quote.dto.AdminQuoteSummary;

/** 담당자 7 · F-OPS-021: 관리자 조립 API가 QUOTE 테이블을 우회 조회하지 않도록 제공하는 읽기 계약입니다. */
public interface AdminQuoteSummaryReader {

    Map<Long, AdminQuoteSummary> findSummaries(List<Long> serviceRequestIds);
}
