package nct.trade.port;

import java.util.List;

import nct.trade.dto.AdminTradeDisputeQuery;
import nct.trade.dto.AdminTradeDisputeRecord;

/** 담당자 7 · F-OPS-005: 운영 화면이 거래 소유 테이블을 직접 조회하지 않도록 제공하는 읽기 포트입니다. */
public interface AdminTradeDisputeReader {

    long count(AdminTradeDisputeQuery query);

    List<AdminTradeDisputeRecord> findPage(AdminTradeDisputeQuery query);

    AdminTradeDisputeRecord findById(long disputeSn);
}
