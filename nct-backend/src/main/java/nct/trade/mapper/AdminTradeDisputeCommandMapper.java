package nct.trade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.trade.dto.AdminTradeDisputeDecisionTarget;

/** 담당자 7 · F-OPS-006: 관리자 분쟁 판정용 조건부 상태변경 Mapper입니다. */
@Mapper
public interface AdminTradeDisputeCommandMapper {

    AdminTradeDisputeDecisionTarget findForUpdate(@Param("disputeSn") long disputeSn);

    int updateTradeStatus(
            @Param("tradeSn") long tradeSn,
            @Param("expectedStatusCode") String expectedStatusCode,
            @Param("targetStatusCode") String targetStatusCode,
            @Param("updaterId") String updaterId);

    int updateDisputeDecision(
            @Param("disputeSn") long disputeSn,
            @Param("resultCode") String resultCode,
            @Param("statusCode") String statusCode,
            @Param("reason") String reason,
            @Param("processorUserSn") long processorUserSn,
            @Param("updaterId") String updaterId);

    int insertTradeStatusHistory(
            @Param("tradeSn") long tradeSn,
            @Param("statusCode") String statusCode,
            @Param("reason") String reason,
            @Param("updaterId") String updaterId);
}
