package nct.trade.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.trade.domain.Trade;
import nct.trade.dto.AuctionTradeEscrowInfo;
import nct.trade.dto.TradeAutoCompletionTarget;
import nct.trade.dto.TradeCancellationTarget;
import nct.trade.dto.TradeDetailResponse;
import nct.trade.dto.TradeDeliveryProofFile;
import nct.trade.dto.TradeDeliverySubmitTarget;
import nct.trade.dto.TradeDeliveryProofSubmitRequest;
import nct.trade.dto.TradeConfirmationTarget;
import nct.trade.dto.TradeDisputeTarget;
import nct.trade.dto.TradeListItem;
import nct.trade.dto.MemberActiveTradeTarget;
import nct.trade.dto.SellerTradeStatusItem;
import nct.trade.dto.TradeSettlementReference;
import nct.trade.dto.ServiceTradeCompletionTarget;
import nct.trade.dto.ServiceTradeDetailSource;
import nct.trade.dto.ServiceTradeListItem;

/** 거래 생성과 본인 거래 조회를 담당하는 MyBatis 매퍼다. */
@Mapper
public interface TradeMapper {

    Long findOwnedProductIdForUpdate(
            @Param("productId") long productId,
            @Param("sellerUserId") long sellerUserId);

    Long findMaterialTradeIdByProductId(@Param("productId") long productId);

    /** 경매 취소·환불 흐름이 거래와 원본 입찰 보관금의 연결을 직접 확인한다. */
    AuctionTradeEscrowInfo findAuctionTradeEscrowInfoByProductId(
            @Param("productId") long productId);

    /** 정산 도메인에 거래 유형과 원본 입찰 보관금 참조만 제공한다. */
    TradeSettlementReference findSettlementReferenceByTradeId(
            @Param("tradeId") long tradeId);

    /**
     * 거래 문제 접수·정산 보류 흐름이 같은 트랜잭션에서 사용할 TRADE 잠금 조회다.
     * 소비자는 이 결과를 받은 뒤에만 자신의 TRADE_DISPUTE·SETTLEMENT 계약을 실행한다.
     */
    TradeDisputeTarget findTradeDisputeTargetForUpdate(@Param("tradeId") long tradeId);

    /** 같은 거래의 접수·처리중 분쟁이 있는지 조회한다. TRADE 행 잠금 뒤에 호출한다. */
    boolean hasOpenTradeDispute(@Param("tradeId") long tradeId);

    int insertTradeDispute(
            @Param("tradeId") long tradeId,
            @Param("disputerUserId") long disputerUserId,
            @Param("disputeTypeCode") String disputeTypeCode,
            @Param("content") String content,
            @Param("updaterId") String updaterId);

    /** 서비스 거래 문제 접수 성공 후에만 거래를 보류 상태로 전환한다. */
    int holdServiceTradeForDispute(
            @Param("tradeId") long tradeId,
            @Param("updaterId") String updaterId);

    /** 담당자 7 · F-OPS-020: 제한 대상자의 모든 진행 거래를 잠금 조회합니다. */
    List<MemberActiveTradeTarget> findActiveTradesByMemberForUpdate(@Param("userSn") long userSn);

    /** 담당자 7 · F-OPS-020: 잠금 시점 상태가 유지된 거래만 보류합니다. */
    int holdTradeForMemberRestriction(
            @Param("tradeId") long tradeId,
            @Param("expectedStatusCode") String expectedStatusCode,
            @Param("updaterId") String updaterId);

    /** 서비스 거래 완료 처리 전 거래 행을 잠가 당사자·금액·분쟁 상태를 재검증한다. */
    ServiceTradeCompletionTarget findServiceTradeCompletionTargetForUpdate(
            @Param("tradeId") long tradeId);

    int startServiceCompletionRequest(
            @Param("tradeId") long tradeId,
            @Param("autoCompleteAt") LocalDateTime autoCompleteAt,
            @Param("updaterId") String updaterId);

    int completeServiceTrade(
            @Param("tradeId") long tradeId,
            @Param("updaterId") String updaterId);

    List<Long> findExpiredServiceAutoCompletionTradeIds(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    /** 거래 생성 시 배송/직거래 후속 처리를 결정할 상품 거래 방식을 조회한다. */
    String findProductTradeMethod(@Param("productId") long productId);

    int insertMaterialTrade(Trade trade);

    /** 선택 견적당 거래 1건 규칙의 멱등 재호출 확인용 조회다. */
    Long findServiceTradeIdByQuoteId(@Param("quoteId") long quoteId);

    int insertServiceTrade(Trade trade);

    /** MemberService가 조회한 낙찰자 배송정보를 거래 시점 스냅샷으로 저장한다. */
    int insertDeliverySnapshot(
            @Param("tradeId") long tradeId,
            @Param("recipientName") String recipientName,
            @Param("recipientPhone") String recipientPhone,
            @Param("zip") String zip,
            @Param("address") String address,
            @Param("detailAddress") String detailAddress);

    int insertStatusHistory(
            @Param("tradeId") long tradeId,
            @Param("statusCode") String statusCode,
            @Param("reason") String reason);

    List<TradeListItem> findMyMaterialTrades(
            @Param("userId") long userId,
            @Param("role") String role,
            @Param("statusCode") String statusCode,
            @Param("keyword") String keyword);

    /** F-AUC-005가 AUCTION 상태와 결합할 수 있도록 판매자 본인의 생성 거래 상태만 반환한다. */
    List<SellerTradeStatusItem> findMySellerTradeStatuses(
            @Param("sellerUserId") long sellerUserId);

    /** ProductService가 이미 조회한 상품 목록에 붙일 물건 거래 상태를 일괄 조회한다. */
    List<SellerTradeStatusItem> findTradeStatusesByProducts(
            @Param("prdSns") List<Long> prdSns);

    TradeDetailResponse findMyMaterialTradeDetail(
            @Param("tradeId") long tradeId,
            @Param("userId") long userId);

    /** 서비스 거래 당사자만 요청서·선택 견적·정산 상태를 함께 조회한다. */
    ServiceTradeDetailSource findMyServiceTradeDetail(
            @Param("tradeId") long tradeId,
            @Param("userId") long userId);

    /** 서비스 거래 당사자의 목록 조회다. 서비스 요청 주소 등 민감 정보는 조회하지 않는다. */
    List<ServiceTradeListItem> findMyServiceTrades(
            @Param("userId") long userId,
            @Param("role") String role,
            @Param("statusCode") String statusCode);

    List<TradeDeliveryProofFile> findTradeDeliveryProofFiles(
            @Param("deliveryId") long deliveryId);

    TradeDeliverySubmitTarget findMyDeliveryTradeForUpdate(
            @Param("tradeId") long tradeId,
            @Param("sellerUserId") long sellerUserId);

    int ensureTradeDelivery(@Param("tradeId") long tradeId);

    Long findDeliveryIdByTradeIdForUpdate(@Param("tradeId") long tradeId);

    int updateDeliveryMessage(
            @Param("deliveryId") long deliveryId,
            @Param("deliveryMessage") String deliveryMessage,
            @Param("updaterId") String updaterId);

    int insertTradeDeliveryFile(
            @Param("deliveryId") long deliveryId,
            @Param("fileId") long fileId,
            @Param("sortOrder") int sortOrder);

    int startDelivery(
            @Param("tradeId") long tradeId,
            @Param("updaterId") String updaterId);

    /** 판매자가 직거래 일정을 제안한 뒤 거래 상태를 직거래 진행으로 전이한다. */
    int startOfflineTrade(
            @Param("tradeId") long tradeId,
            @Param("updaterId") String updaterId);

    Long findMyOfflineTradeIdForUpdate(
            @Param("tradeId") long tradeId,
            @Param("sellerUserId") long sellerUserId);

    int upsertOfflineSchedule(
            @Param("tradeId") long tradeId,
            @Param("meetingDateTime") LocalDateTime meetingDateTime,
            @Param("meetingPlace") String meetingPlace,
            @Param("meetingAddress") String meetingAddress);

    /** 거래 당사자 본인의 물건 거래를 잠가 완료 확인과 중복 요청이 경합하지 않게 한다. */
    TradeConfirmationTarget findMyTradeForConfirmationForUpdate(
            @Param("tradeId") long tradeId,
            @Param("userId") long userId);

    /** 직거래 일정이 실제로 저장됐는지 확인해 일정 전 완료 처리를 차단한다. */
    boolean hasOfflineSchedule(@Param("tradeId") long tradeId);

    /** 확인 대기 상태와 자동완료 기준 시각을 한 트랜잭션 안에서 함께 저장한다. */
    int startCompletionConfirmation(
            @Param("tradeId") long tradeId,
            @Param("autoCompleteAt") LocalDateTime autoCompleteAt,
            @Param("updaterId") String updaterId);

    /** 첫 확인자가 아닌 상대방만 확인 대기 거래를 완료로 바꾼다. */
    int completeConfirmationByCounterpart(
            @Param("tradeId") long tradeId,
            @Param("completionRequesterId") String completionRequesterId,
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

    /** 관리자 취소 승인 전에 거래 행을 잠가 상태 전이와 중복 판단이 경합하지 않게 한다. */
    TradeCancellationTarget findMaterialTradeForCancellationForUpdate(
            @Param("tradeId") long tradeId);

    /** 취소 가능한 상태만 취소로 바꿔, 잠금 해제 전 상태 변경에도 안전하게 대응한다. */
    int cancelMaterialTrade(
            @Param("tradeId") long tradeId,
            @Param("updaterId") String updaterId);
}
