package nct.trade.mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.trade.domain.Trade;
import nct.trade.dto.AuctionTradeEscrowInfo;
import nct.trade.dto.AuctionBidTradeReference;
import nct.trade.dto.TradeAutoCompletionTarget;
import nct.trade.dto.TradeCancellationTarget;
import nct.trade.dto.TradeDetailResponse;
import nct.trade.dto.TradeDeliveryProofFile;
import nct.trade.dto.TradeDeliverySubmitTarget;
import nct.trade.dto.TradeDeliveryProofSubmitRequest;
import nct.trade.dto.TradeConfirmationTarget;
import nct.trade.dto.TradeDisputeTarget;
import nct.trade.dto.TradeDisputeRegistration;
import nct.trade.dto.TradeListItem;
import nct.trade.dto.MemberActiveTradeTarget;
import nct.trade.dto.SellerTradeStatusItem;
import nct.trade.dto.TradeSettlementReference;
import nct.trade.dto.ServiceTradeCompletionTarget;
import nct.trade.dto.ServiceTradeDetailSource;
import nct.trade.dto.ServiceTradeAddressSource;
import nct.trade.dto.ServiceTradeListItem;
import nct.trade.dto.ServiceScheduleHistoryItem;
import nct.trade.dto.ServiceScheduleCancellationPending;
import nct.trade.dto.AdminServiceTradeSummary;

/** 거래 생성과 본인 거래 조회를 담당하는 MyBatis 매퍼다. */
@Mapper
public interface TradeMapper {

    /** 담당자 7 · F-OPS-010: 관리자 대시보드용 전체 거래 수를 반환합니다. */
    long countAllTrades();

    Long findOwnedProductIdForUpdate(
            @Param("productId") long productId,
            @Param("sellerUserId") long sellerUserId);

    Long findMaterialTradeIdByProductId(@Param("productId") long productId);

    /** 경매 취소·환불 흐름이 거래와 원본 입찰 보관금의 연결을 직접 확인한다. */
    AuctionTradeEscrowInfo findAuctionTradeEscrowInfoByProductId(
            @Param("productId") long productId);

    /** 경매 입찰 이력이 본인의 물건 거래 상세로 이동할 수 있도록 BID_SN 기준으로 일괄 연결한다. */
    List<AuctionBidTradeReference> findAuctionBidTradeReferencesByBuyerAndBidSns(
            @Param("buyerUserId") long buyerUserId,
            @Param("bidSns") Collection<Long> bidSns);

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

    int insertTradeDispute(TradeDisputeRegistration registration);

    /** 생성된 분쟁에 검증 완료된 증빙 파일을 표시 순서대로 연결합니다. */
    int insertTradeDisputeFile(
            @Param("disputeSn") long disputeSn,
            @Param("fileSn") long fileSn,
            @Param("sortOrder") int sortOrder,
            @Param("registrantId") String registrantId);

    /** 서비스 거래 문제 접수 성공 후에만 거래를 보류 상태로 전환한다. */
    int holdServiceTradeForDispute(
            @Param("tradeId") long tradeId,
            @Param("updaterId") String updaterId);

    /** 담당자 7 · F-OPS-020: 제한 대상자의 모든 진행 거래를 잠금 조회합니다. */
    List<MemberActiveTradeTarget> findActiveTradesByMemberForUpdate(@Param("userSn") long userSn);

    // @ai_generated: F-AUTH-011/POL-AUTH-013 - 탈퇴 전 하드 차단용. 물건(SLLR/BYPR)·서비스(REQ/PRV)
    // 거래 모두, 진행중·배송중·완료확인대기·보류(TRDC0003/0004/0005/0007) 건수를 센다.
    // findActiveTradesByMemberForUpdate(위)는 물건 거래 전용이고 보류를 포함하지 않아 재사용하지 않는다.
    int countBlockingTradesByUser(@Param("userSn") long userSn);

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

    /** 담당자 7 · F-OPS-021: 서비스 요청별 거래·진행 중 분쟁 상태를 한 번에 조회합니다. */
    List<AdminServiceTradeSummary> findAdminServiceTradeSummaries(
            @Param("serviceRequestIds") Collection<Long> serviceRequestIds);

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

    /**
     * @ai_generated (담당자1 황희준, 2026-08-07, 조율 대기): auctionId 정식 경로를 기존 tradeId
     * 상세 계약으로 연결한다. productId는 AuctionService.findProductIdByAuctionId 계약으로 얻은
     * 값을 넘긴다 - 이 메서드는 AUCTION을 직접 JOIN하지 않는다.
     */
    Long findMyMaterialTradeIdByProductId(
            @Param("productId") long productId,
            @Param("userId") long userId);

    /** 서비스 거래 당사자만 요청서·선택 견적·정산 상태를 함께 조회한다. */
    ServiceTradeDetailSource findMyServiceTradeDetail(
            @Param("tradeId") long tradeId,
            @Param("userId") long userId);

    /** 관리자 전용 서비스 거래 상세는 당사자 전용 조회와 별도 계약으로 제공합니다. */
    ServiceTradeDetailSource findAdminServiceTradeDetail(@Param("tradeId") long tradeId);

    /** 선택 견적 거래의 두 당사자에게만 요청서 정확 주소 암호문을 제공한다. */
    List<ServiceTradeAddressSource> findMyServiceTradeAddresses(
            @Param("tradeId") long tradeId,
            @Param("userId") long userId);

    /** 일정 이벤트 형식으로 저장한 서비스 거래 상태 이력만 상세 화면에 제공한다. */
    List<ServiceScheduleHistoryItem> findServiceScheduleHistory(@Param("tradeId") long tradeId);

    /** 같은 거래에서 상대방이 남긴 미처리 일정 취소 요청 한 건을 조회한다. */
    ServiceScheduleCancellationPending findPendingServiceScheduleCancellation(
            @Param("tradeId") long tradeId);

    /** 상호 동의 취소에 한해 서비스 진행 거래를 취소 상태로 바꾼다. */
    int cancelServiceTrade(
            @Param("tradeId") long tradeId,
            @Param("updaterId") String updaterId);

    /** 서비스 거래 당사자의 목록 조회다. 서비스 요청 주소 등 민감 정보는 조회하지 않는다. */
    List<ServiceTradeListItem> findMyServiceTrades(
            @Param("userId") long userId,
            @Param("role") String role,
            @Param("statusCode") String statusCode,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("size") int size);

    /** 서비스 거래 목록과 동일한 당사자·필터 조건의 전체 건수다. */
    long countMyServiceTrades(
            @Param("userId") long userId,
            @Param("role") String role,
            @Param("statusCode") String statusCode,
            @Param("keyword") String keyword);

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

    int deleteOfflineSchedule(@Param("tradeId") long tradeId);

    int resetOfflineTrade(
            @Param("tradeId") long tradeId,
            @Param("updaterId") String updaterId);

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
