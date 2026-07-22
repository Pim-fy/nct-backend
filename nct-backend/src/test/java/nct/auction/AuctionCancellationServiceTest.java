package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.dto.AuctionBidRequest;
import nct.auction.dto.AuctionBuyNowRequest;
import nct.auction.dto.AuctionCancelRequestResponse;
import nct.auction.service.AuctionCancellationService;
import nct.auction.service.AuctionService;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

@SpringBootTest
@Transactional
class AuctionCancellationServiceTest {

    @Autowired AuctionCancellationService cancellationService;
    @Autowired AuctionService auctionService;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("진행 중 경매의 판매자 취소 요청은 이전 상태를 저장하고 취소요청 상태로 변경한다")
    void requestCancellationForActiveAuction() {
        long sellerSn = insertUser("t_auc_cancel_seller");
        long prdSn = insertProduct(sellerSn, BigDecimal.valueOf(30000));
        long aucSn = insertAuction(prdSn, AuctionStatusCode.ACTIVE);

        AuctionCancelRequestResponse response = cancellationService.requestCancellation(
                aucSn,
                sellerSn,
                "  상품 상태 변경  ");

        assertThat(response.getAucCnlReqSn()).isNotNull();
        assertThat(response.getAucSn()).isEqualTo(aucSn);
        assertThat(response.getPrevAucStatusCd()).isEqualTo(AuctionStatusCode.ACTIVE);
        assertThat(response.getAucStatusCd()).isEqualTo(AuctionStatusCode.CANCEL_REQUESTED);
        assertThat(auctionStatus(aucSn)).isEqualTo(AuctionStatusCode.CANCEL_REQUESTED);

        Map<String, Object> request = pendingRequest(aucSn);
        assertThat(request.get("PREV_AUC_STATUS_CD")).isEqualTo(AuctionStatusCode.ACTIVE);
        assertThat(request.get("AUC_CNL_REQ_RSN_CN")).isEqualTo("상품 상태 변경");
        assertThat(request.get("AUC_CNL_REQ_APRV_YN")).isNull();
    }

    @Test
    @DisplayName("종료된 경매도 취소 요청할 수 있고 이전 상태로 종료를 저장한다")
    void requestCancellationForEndedAuction() {
        long sellerSn = insertUser("t_auc_cancel_seller");
        long prdSn = insertProduct(sellerSn, null);
        long aucSn = insertAuction(prdSn, AuctionStatusCode.ENDED);

        AuctionCancelRequestResponse response = cancellationService.requestCancellation(
                aucSn,
                sellerSn,
                "낙찰 이후 상품 상태 변경");

        assertThat(response.getPrevAucStatusCd()).isEqualTo(AuctionStatusCode.ENDED);
        assertThat(auctionStatus(aucSn)).isEqualTo(AuctionStatusCode.CANCEL_REQUESTED);
        assertThat(pendingRequest(aucSn).get("PREV_AUC_STATUS_CD"))
                .isEqualTo(AuctionStatusCode.ENDED);
    }

    @Test
    @DisplayName("판매자가 아닌 회원은 경매 취소를 요청할 수 없다")
    void rejectCancellationFromNonOwner() {
        long sellerSn = insertUser("t_auc_cancel_seller");
        long otherUsrSn = insertUser("t_auc_cancel_other");
        long prdSn = insertProduct(sellerSn, null);
        long aucSn = insertAuction(prdSn, AuctionStatusCode.ACTIVE);

        assertThatThrownBy(() -> cancellationService.requestCancellation(aucSn, otherUsrSn, "판매 진행 불가"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_NOT_OWNER);

        assertThat(auctionStatus(aucSn)).isEqualTo(AuctionStatusCode.ACTIVE);
        assertThat(pendingRequestCount(aucSn)).isZero();
    }

    @Test
    @DisplayName("준비·유찰·취소 상태의 경매는 취소 요청할 수 없다")
    void rejectCancellationFromInvalidStatuses() {
        long sellerSn = insertUser("t_auc_cancel_seller");

        for (String statusCode : new String[] {
                AuctionStatusCode.READY,
                AuctionStatusCode.FAILED,
                AuctionStatusCode.CANCELED}) {
            long prdSn = insertProduct(sellerSn, null);
            long aucSn = insertAuction(prdSn, statusCode);

            assertThatThrownBy(() -> cancellationService.requestCancellation(aucSn, sellerSn, "판매 진행 불가"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.PRODUCT_CANCEL_INVALID_STATUS);
        }
    }

    @Test
    @DisplayName("같은 경매에 처리 대기 취소 요청을 중복 등록할 수 없다")
    void rejectDuplicatePendingRequest() {
        long sellerSn = insertUser("t_auc_cancel_seller");
        long prdSn = insertProduct(sellerSn, null);
        long aucSn = insertAuction(prdSn, AuctionStatusCode.ACTIVE);
        cancellationService.requestCancellation(aucSn, sellerSn, "첫 번째 사유");

        assertThatThrownBy(() -> cancellationService.requestCancellation(aucSn, sellerSn, "두 번째 사유"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUCTION_CANCEL_REQUEST_ALREADY_PENDING);

        assertThat(pendingRequestCount(aucSn)).isEqualTo(1);
    }

    @Test
    @DisplayName("취소 사유가 공백이면 요청을 등록하지 않는다")
    void rejectBlankReason() {
        long sellerSn = insertUser("t_auc_cancel_seller");
        long prdSn = insertProduct(sellerSn, null);
        long aucSn = insertAuction(prdSn, AuctionStatusCode.ACTIVE);

        assertThatThrownBy(() -> cancellationService.requestCancellation(aucSn, sellerSn, "   "))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        assertThat(auctionStatus(aucSn)).isEqualTo(AuctionStatusCode.ACTIVE);
        assertThat(pendingRequestCount(aucSn)).isZero();
    }

    @Test
    @DisplayName("취소요청 상태에서는 입찰과 즉시구매를 모두 차단한다")
    void cancelRequestedAuctionRejectsBidAndBuyNow() {
        long sellerSn = insertUser("t_auc_cancel_seller");
        long bidderSn = insertUser("t_auc_cancel_bidder");
        long prdSn = insertProduct(sellerSn, BigDecimal.valueOf(30000));
        long aucSn = insertAuction(prdSn, AuctionStatusCode.ACTIVE);
        cancellationService.requestCancellation(aucSn, sellerSn, "상품 정보 오류");

        AuctionBidRequest bidRequest = new AuctionBidRequest();
        bidRequest.setBidAmount(BigDecimal.valueOf(12000));

        assertThatThrownBy(() -> auctionService.placeBid(aucSn, bidderSn, bidRequest))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
        assertThatThrownBy(() -> auctionService.buyNow(aucSn, bidderSn, new AuctionBuyNowRequest()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("진행 경매 취소 승인 시 최고입찰을 예외취소하고 홀딩을 반환한다")
    void approveActiveAuctionCancellationReleasesBidHold() {
        long sellerSn = insertUser("t_auc_cancel_seller");
        long bidderSn = insertUser("t_auc_cancel_bidder");
        long adminSn = insertUser("t_auc_cancel_admin");
        long prdSn = insertProduct(sellerSn, BigDecimal.valueOf(30000));
        long aucSn = insertAuction(prdSn, AuctionStatusCode.ACTIVE);
        creditAvailable(bidderSn, 50000);

        AuctionBidRequest bidRequest = new AuctionBidRequest();
        bidRequest.setBidAmount(BigDecimal.valueOf(12000));
        auctionService.placeBid(aucSn, bidderSn, bidRequest);
        long bidSn = latestBidSn(aucSn);

        AuctionCancelRequestResponse request = cancellationService.requestCancellation(
                aucSn, sellerSn, "판매 진행 불가");
        cancellationService.approveCancellation(
                request.getAucCnlReqSn(), adminSn, "관리자 취소 승인");

        assertThat(auctionStatus(aucSn)).isEqualTo(AuctionStatusCode.CANCELED);
        assertThat(bidStatus(bidSn)).isEqualTo("BIDC0004");
        assertThat(activeHoldAmount(bidderSn, bidSn)).isZero();
        assertThat(availablePointAmount(bidderSn)).isEqualTo(50000);

        Map<String, Object> processed = processedRequest(request.getAucCnlReqSn());
        assertThat(processed.get("AUC_CNL_REQ_APRV_YN")).isEqualTo("Y");
        assertThat(((Number) processed.get("PROC_USR_SN")).longValue()).isEqualTo(adminSn);
        assertThat(processed.get("AUC_CNL_REQ_PROC_RSN_CN")).isEqualTo("관리자 취소 승인");
        assertThat(processed.get("AUC_CNL_REQ_PROC_DT")).isNotNull();
    }

    @Test
    @DisplayName("종료 경매 취소 승인 시 거래와 낙찰 입찰을 취소하고 BID 보관금을 환불한다")
    void approveEndedAuctionCancellationRefundsBidEscrow() {
        long sellerSn = insertUser("t_auc_cancel_seller");
        long buyerSn = insertUser("t_auc_cancel_buyer");
        long adminSn = insertUser("t_auc_cancel_admin");
        long prdSn = insertProduct(sellerSn, BigDecimal.valueOf(30000));
        long aucSn = insertAuction(prdSn, AuctionStatusCode.ACTIVE);
        creditAvailable(buyerSn, 50000);

        auctionService.buyNow(aucSn, buyerSn, new AuctionBuyNowRequest());
        long bidSn = latestBidSn(aucSn);
        long tradeSn = materialTradeSn(prdSn);
        assertThat(materialTradeBidSn(tradeSn)).isEqualTo(bidSn);

        AuctionCancelRequestResponse request = cancellationService.requestCancellation(
                aucSn, sellerSn, "낙찰 이후 상품 상태 변경");
        cancellationService.approveCancellation(
                request.getAucCnlReqSn(), adminSn, "관리자 취소 승인");

        assertThat(auctionStatus(aucSn)).isEqualTo(AuctionStatusCode.CANCELED);
        assertThat(tradeStatus(tradeSn)).isEqualTo("TRDC0008");
        assertThat(bidStatus(bidSn)).isEqualTo("BIDC0004");
        assertThat(availablePointAmount(buyerSn)).isEqualTo(50000);
        assertThat(refundLedgerCount(buyerSn, bidSn)).isEqualTo(1);
        assertThat(processedRequest(request.getAucCnlReqSn()).get("AUC_CNL_REQ_APRV_YN"))
                .isEqualTo("Y");
    }

    @Test
    @DisplayName("취소 요청 반려 시 거래와 포인트를 변경하지 않고 요청 전 경매 상태로 복귀한다")
    void rejectCancellationRestoresPreviousAuctionStatus() {
        long sellerSn = insertUser("t_auc_cancel_seller");
        long adminSn = insertUser("t_auc_cancel_admin");
        long prdSn = insertProduct(sellerSn, null);
        long aucSn = insertAuction(prdSn, AuctionStatusCode.ACTIVE);
        AuctionCancelRequestResponse request = cancellationService.requestCancellation(
                aucSn, sellerSn, "상품 정보 오류");

        cancellationService.rejectCancellation(
                request.getAucCnlReqSn(), adminSn, "판매를 계속할 수 있음");

        assertThat(auctionStatus(aucSn)).isEqualTo(AuctionStatusCode.ACTIVE);
        Map<String, Object> processed = processedRequest(request.getAucCnlReqSn());
        assertThat(processed.get("AUC_CNL_REQ_APRV_YN")).isEqualTo("N");
        assertThat(processed.get("AUC_CNL_REQ_PROC_RSN_CN")).isEqualTo("판매를 계속할 수 있음");
    }

    @Test
    @DisplayName("이미 처리된 취소 요청은 재승인하거나 재반려할 수 없다")
    void processedCancellationCannotBeProcessedAgain() {
        long sellerSn = insertUser("t_auc_cancel_seller");
        long adminSn = insertUser("t_auc_cancel_admin");
        long prdSn = insertProduct(sellerSn, null);
        long aucSn = insertAuction(prdSn, AuctionStatusCode.ACTIVE);
        AuctionCancelRequestResponse request = cancellationService.requestCancellation(
                aucSn, sellerSn, "상품 정보 오류");
        cancellationService.rejectCancellation(
                request.getAucCnlReqSn(), adminSn, "반려 처리");

        assertThatThrownBy(() -> cancellationService.approveCancellation(
                request.getAucCnlReqSn(), adminSn, "재승인"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_PROCESSED);
        assertThatThrownBy(() -> cancellationService.rejectCancellation(
                request.getAucCnlReqSn(), adminSn, "재반려"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("종료 경매의 거래 보관금 계약이 없으면 취소 승인을 확정하지 않는다")
    void approveEndedAuctionRequiresTradeEscrowInfo() {
        long sellerSn = insertUser("t_auc_cancel_seller");
        long adminSn = insertUser("t_auc_cancel_admin");
        long prdSn = insertProduct(sellerSn, null);
        long aucSn = insertAuction(prdSn, AuctionStatusCode.ENDED);
        AuctionCancelRequestResponse request = cancellationService.requestCancellation(
                aucSn, sellerSn, "낙찰 이후 상품 상태 변경");

        assertThatThrownBy(() -> cancellationService.approveCancellation(
                request.getAucCnlReqSn(), adminSn, "관리자 취소 승인"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        assertThat(auctionStatus(aucSn)).isEqualTo(AuctionStatusCode.CANCEL_REQUESTED);
        assertThat(pendingRequestCount(aucSn)).isEqualTo(1);
    }

    private long insertUser(String prefix) {
        String loginId = prefix + "_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (
                    USR_LOGIN_ID,
                    USR_PSWD_HASH,
                    USR_NM,
                    USR_EML,
                    USR_STATUS_CD,
                    USR_ROLE_CD,
                    USR_ADDR,
                    USR_DADDR,
                    USR_ZIP
                )
                VALUES (?, '{noop}test', ?, ?, 'USRC0001', 'ROLE_USER', '테스트 주소', '101호', '12345')
                """, loginId, prefix, loginId + "@test.local");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertProduct(long sellerSn, BigDecimal instantBuyAmount) {
        jdbc.update("""
                INSERT INTO PRODUCT (
                    USR_SN,
                    CAT_SN,
                    PRD_NM,
                    PRD_STATUS_CD,
                    PRD_START_AMT,
                    PRD_IBY_AMT,
                    PRD_TRD_METHOD_CD
                )
                VALUES (?, 2, '취소 요청 테스트 상품', 'PRDC0002', 10000, ?, 'TRDC0009')
                """, sellerSn, instantBuyAmount);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertAuction(long prdSn, String statusCode) {
        jdbc.update("""
                INSERT INTO AUCTION (
                    PRD_SN,
                    AUC_STATUS_CD,
                    AUC_CUR_AMT,
                    AUC_BID_UNIT_AMT,
                    AUC_START_DT,
                    AUC_END_DT
                )
                VALUES (?, ?, 10000, 1000, ?, ?)
                """,
                prdSn,
                statusCode,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1));
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private String auctionStatus(long aucSn) {
        return jdbc.queryForObject(
                "SELECT AUC_STATUS_CD FROM AUCTION WHERE AUC_SN = ?",
                String.class,
                aucSn);
    }

    private int pendingRequestCount(long aucSn) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM AUCTION_CANCEL_REQUEST
                WHERE AUC_SN = ?
                  AND AUC_CNL_REQ_APRV_YN IS NULL
                """, Integer.class, aucSn);
    }

    private Map<String, Object> pendingRequest(long aucSn) {
        return jdbc.queryForMap("""
                SELECT PREV_AUC_STATUS_CD, AUC_CNL_REQ_RSN_CN, AUC_CNL_REQ_APRV_YN
                FROM AUCTION_CANCEL_REQUEST
                WHERE AUC_SN = ?
                  AND AUC_CNL_REQ_APRV_YN IS NULL
                """, aucSn);
    }

    private Map<String, Object> processedRequest(long cancelRequestSn) {
        return jdbc.queryForMap("""
                SELECT AUC_CNL_REQ_APRV_YN,
                       PROC_USR_SN,
                       AUC_CNL_REQ_PROC_RSN_CN,
                       AUC_CNL_REQ_PROC_DT
                FROM AUCTION_CANCEL_REQUEST
                WHERE AUC_CNL_REQ_SN = ?
                """, cancelRequestSn);
    }

    private void creditAvailable(long usrSn, long amount) {
        jdbc.update("""
                INSERT INTO POINT_LEDGER (
                    USR_SN,
                    PT_LDG_PT_TYPE_CD,
                    PT_LDG_TYPE_CD,
                    PT_LDG_AMT,
                    PT_LDG_BAL_AFTER_AMT,
                    PT_LDG_RSN_CN
                )
                VALUES (?, 'PTLC0001', 'PTLC0004', ?, ?, '테스트 충전')
                """, usrSn, amount, amount);
    }

    private long latestBidSn(long aucSn) {
        return jdbc.queryForObject("""
                SELECT BID_SN
                FROM BID
                WHERE AUC_SN = ?
                ORDER BY BID_SN DESC
                LIMIT 1
                """, Long.class, aucSn);
    }

    private String bidStatus(long bidSn) {
        return jdbc.queryForObject(
                "SELECT BID_STATUS_CD FROM BID WHERE BID_SN = ?",
                String.class,
                bidSn);
    }

    private long activeHoldAmount(long usrSn, long bidSn) {
        Long amount = jdbc.queryForObject("""
                SELECT COALESCE(SUM(PT_LDG_AMT), 0)
                FROM POINT_LEDGER
                WHERE USR_SN = ?
                  AND PT_LDG_PT_TYPE_CD = 'PTLC0002'
                  AND PT_LDG_REF_TYPE_CD = ?
                  AND PT_LDG_REF_SN = ?
                """, Long.class, usrSn, RefType.BID.getCode(), bidSn);
        return amount == null ? 0 : amount;
    }

    private long availablePointAmount(long usrSn) {
        Long amount = jdbc.queryForObject("""
                SELECT COALESCE(SUM(PT_LDG_AMT), 0)
                FROM POINT_LEDGER
                WHERE USR_SN = ?
                  AND PT_LDG_PT_TYPE_CD = 'PTLC0001'
                """, Long.class, usrSn);
        return amount == null ? 0 : amount;
    }

    private long materialTradeSn(long prdSn) {
        return jdbc.queryForObject("""
                SELECT TRD_SN
                FROM TRADE
                WHERE PRD_SN = ?
                  AND TRD_TYPE_CD = 'TRDC0001'
                """, Long.class, prdSn);
    }

    private long materialTradeBidSn(long tradeSn) {
        return jdbc.queryForObject(
                "SELECT BID_SN FROM TRADE WHERE TRD_SN = ?",
                Long.class,
                tradeSn);
    }

    private String tradeStatus(long tradeSn) {
        return jdbc.queryForObject(
                "SELECT TRD_STATUS_CD FROM TRADE WHERE TRD_SN = ?",
                String.class,
                tradeSn);
    }

    private int refundLedgerCount(long usrSn, long bidSn) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM POINT_LEDGER
                WHERE USR_SN = ?
                  AND PT_LDG_TYPE_CD = 'PTLC0013'
                  AND PT_LDG_REF_TYPE_CD = ?
                  AND PT_LDG_REF_SN = ?
                """, Integer.class, usrSn, RefType.BID.getCode(), bidSn);
    }
}
