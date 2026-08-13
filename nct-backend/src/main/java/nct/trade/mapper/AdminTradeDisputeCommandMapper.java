package nct.trade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.trade.dto.AdminTradeDisputeDecisionTarget;

/** 담당자 7 · F-OPS-005/006: 관리자 거래 신고 판정용 조건부 상태변경 Mapper입니다. */
@Mapper
public interface AdminTradeDisputeCommandMapper {

    AdminTradeDisputeDecisionTarget findForUpdate(@Param("reportSn") long reportSn);

    int updateTradeStatus(
            @Param("tradeSn") long tradeSn,
            @Param("expectedStatusCode") String expectedStatusCode,
            @Param("targetStatusCode") String targetStatusCode,
            @Param("remainingSeconds") Long remainingSeconds,
            @Param("updaterId") String updaterId);

    int updateTradeReportResult(
            @Param("reportSn") long reportSn,
            @Param("resultCode") String resultCode,
            @Param("updaterId") String updaterId);

    int insertTradeStatusHistory(
            @Param("tradeSn") long tradeSn,
            @Param("statusCode") String statusCode,
            @Param("reason") String reason,
            @Param("updaterId") String updaterId);
}
