package nct.trade.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.trade.domain.Trade;
import nct.trade.dto.TradeAutoCompletionTarget;
import nct.trade.dto.TradeDetailResponse;
import nct.trade.dto.TradeConfirmationTarget;
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

    /** 구매자 본인의 물건 거래를 잠가 완료 확인 요청과 중복 요청이 경합하지 않게 한다. */
    TradeConfirmationTarget findBuyerTradeForConfirmationForUpdate(
            @Param("tradeId") long tradeId,
            @Param("buyerUserId") long buyerUserId);

    /** 확인 대기 상태와 자동완료 기준 시각을 한 트랜잭션 안에서 함께 저장한다. */
    int startCompletionConfirmation(
            @Param("tradeId") long tradeId,
            @Param("autoCompleteAt") LocalDateTime autoCompleteAt,
            @Param("updaterId") String updaterId);

    /** 자동 완료 시각이 지난 확인 대기 거래를 배치 단위로 조회한다. */
    List<Long> findExpiredAutoCompletionTradeIds(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    /** 자동 완료 대상 행을 잠가 다른 스케줄러 실행과 중복 완료되지 않게 한다. */
    TradeAutoCompletionTarget findAutoCompletionTargetForUpdate(
            @Param("tradeId") long tradeId);

    /** 잠금 뒤에도 상태·시각 조건을 한번 더 검사해 완료 상태로 전환한다. */
    int completeExpiredConfirmation(
            @Param("tradeId") long tradeId,
            @Param("now") LocalDateTime now,
            @Param("updaterId") String updaterId);
}
