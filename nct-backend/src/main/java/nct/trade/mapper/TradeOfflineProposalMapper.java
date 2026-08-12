package nct.trade.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.trade.dto.TradeOfflineScheduleProposal;
import nct.trade.dto.TradeOfflineTradeTarget;

/** 직거래 일정 제안·응답 이력을 저장하고 조회하는 매퍼다. */
@Mapper
public interface TradeOfflineProposalMapper {

    TradeOfflineTradeTarget findMyOfflineTradeForUpdate(
            @Param("tradeId") long tradeId,
            @Param("userId") long userId);

    TradeOfflineTradeTarget findMyOfflineTrade(
            @Param("tradeId") long tradeId,
            @Param("userId") long userId);

    TradeOfflineScheduleProposal findPendingProposal(@Param("tradeId") long tradeId);

    /** 일정 신규·변경 제안만 당사자별 누적 횟수로 센다. 일정 취소 제안은 별도 흐름이라 제외한다. */
    int countScheduleProposalsByTradeAndProposer(
            @Param("tradeId") long tradeId,
            @Param("userId") long userId);

    TradeOfflineScheduleProposal findProposalForUpdate(
            @Param("tradeId") long tradeId,
            @Param("proposalId") long proposalId);

    int insertProposal(TradeOfflineScheduleProposal proposal);

    int updateProposalStatus(
            @Param("proposalId") long proposalId,
            @Param("statusCode") String statusCode,
            @Param("responderUserId") long responderUserId,
            @Param("reason") String reason);

    int supersedeAcceptedScheduleProposals(
            @Param("tradeId") long tradeId,
            @Param("proposalId") long proposalId);

    List<TradeOfflineScheduleProposal> findProposalHistory(@Param("tradeId") long tradeId);
}
