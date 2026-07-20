package nct.trade.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.trade.domain.Trade;
import nct.trade.dto.TradeDetailResponse;
import nct.trade.dto.TradeListItem;

/** 거래 생성과 본인 거래 조회를 담당하는 MyBatis 매퍼다. */
@Mapper
public interface TradeMapper {

    Long findOwnedProductIdForUpdate(
            @Param("productId") long productId,
            @Param("sellerUserId") long sellerUserId);

    Long findMaterialTradeIdByProductId(@Param("productId") long productId);

    int insertMaterialTrade(Trade trade);

    int insertStatusHistory(
            @Param("tradeId") long tradeId,
            @Param("statusCode") String statusCode,
            @Param("reason") String reason);

    List<TradeListItem> findMyMaterialTrades(
            @Param("userId") long userId,
            @Param("role") String role,
            @Param("statusCode") String statusCode,
            @Param("keyword") String keyword);

    TradeDetailResponse findMyMaterialTradeDetail(
            @Param("tradeId") long tradeId,
            @Param("userId") long userId);

    Long findMyOfflineTradeIdForUpdate(
            @Param("tradeId") long tradeId,
            @Param("sellerUserId") long sellerUserId);

    int upsertOfflineSchedule(
            @Param("tradeId") long tradeId,
            @Param("meetingDateTime") LocalDateTime meetingDateTime,
            @Param("meetingPlace") String meetingPlace,
            @Param("meetingAddress") String meetingAddress);
}
