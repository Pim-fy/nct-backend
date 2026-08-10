package nct.quote.port;

import java.util.List;

import nct.quote.dto.AdminQuoteListItem;

/** 담당자 3 견적 조회 계약: 관리자 화면에 요청별 견적 원천 목록을 일괄 제공합니다. */
public interface AdminQuoteListReader {

    List<AdminQuoteListItem> findByServiceRequestId(Long serviceRequestId);
}
