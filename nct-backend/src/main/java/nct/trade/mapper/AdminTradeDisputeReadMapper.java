package nct.trade.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.trade.dto.AdminTradeDisputeQuery;
import nct.trade.dto.AdminTradeDisputeRecord;
import nct.trade.dto.TradeDisputeEvidenceFile;

/** 담당자 7 · F-OPS-005: 거래 분쟁 관리자 조회 전용 Mapper입니다. */
@Mapper
public interface AdminTradeDisputeReadMapper {

    long count(@Param("query") AdminTradeDisputeQuery query);

    List<AdminTradeDisputeRecord> findPage(@Param("query") AdminTradeDisputeQuery query);

    AdminTradeDisputeRecord findById(@Param("disputeSn") long disputeSn);

    List<TradeDisputeEvidenceFile> findEvidenceFiles(@Param("disputeSn") long disputeSn);

    int countEvidenceFileLink(
            @Param("disputeSn") long disputeSn,
            @Param("fileSn") long fileSn);
}
