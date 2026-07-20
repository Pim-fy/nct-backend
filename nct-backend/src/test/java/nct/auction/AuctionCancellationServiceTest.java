package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import nct.auction.constant.AuctionStatusCode;
import nct.auction.constant.BidStatusCode;
import nct.auction.dto.AuctionBidRequest;
import nct.auction.dto.AuctionBuyNowRequest;
import nct.auction.service.AuctionCancellationService;
import nct.auction.service.AuctionService;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.point.domain.PointBalance;
import nct.point.service.PointService;

@SpringBootTest
class AuctionCancellationServiceTest {

    @Autowired AuctionCancellationService cancellationService;
    @Autowired AuctionService auctionService;
    @Autowired PointService pointService;
    @Autowired JdbcTemplate jdbc;

    final List<Long> userIds = new ArrayList<>();
    final List<Long> productIds = new ArrayList<>();
    final List<Long> auctionIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ensureSchema();
    }

    @AfterEach
    void cleanUp() {
        if (!auctionIds.isEmpty()) {
            String ids = joinIds(auctionIds);
            jdbc.update("DELETE FROM AUCTION_CANCEL_REQUEST WHERE AUC_SN IN (" + ids + ")");
            jdbc.update("DELETE FROM BID WHERE AUC_SN IN (" + ids + ")");
            jdbc.update("DELETE FROM AUCTION WHERE AUC_SN IN (" + ids + ")");
        }
        if (!productIds.isEmpty()) {
            jdbc.update("DELETE FROM PRODUCT WHERE PRD_SN IN (" + joinIds(productIds) + ")");
        }
        if (!userIds.isEmpty()) {
            String ids = joinIds(userIds);
            jdbc.update("DELETE FROM NOTIFICATION WHERE USR_SN IN (" + ids + ")");
            jdbc.update("DELETE FROM POINT_LEDGER WHERE USR_SN IN (" + ids + ")");
            jdbc.update("DELETE FROM USERS WHERE USR_SN IN (" + ids + ")");
        }
    }

    @Test
    @DisplayName("정상 취소 요청: 진행 경매가 취소요청 상태로 바뀌고 요청 이력이 남는다")
    void requestCancellation() {
        long sellerSn = insertUser("t_cancel_seller");
        long aucSn = insertActiveAuction(sellerSn);

        cancellationService.requestCancellation(aucSn, sellerSn, "판매 불가");

        assertThat(auctionStatus(aucSn)).isEqualTo(AuctionStatusCode.CANCEL_REQUESTED);
        assertThat(pendingCount(aucSn)).isEqualTo(1);
    }

    @Test
    @DisplayName("판매자가 아닌 회원의 취소 요청은 실패한다")
    void requestCancellationByNonSellerFails() {
        long sellerSn = insertUser("t_cancel_seller");
        long otherSn = insertUser("t_cancel_other");
        long aucSn = insertActiveAuction(sellerSn);

        assertThatThrownBy(() -> cancellationService.requestCancellation(aucSn, otherSn, "남의 경매 취소"))
                .isInstanceOf(CustomException.class);
        assertThat(auctionStatus(aucSn)).isEqualTo(AuctionStatusCode.ACTIVE);
    }

    @Test
    @DisplayName("진행 상태가 아닌 경매 취소 요청은 실패한다")
    void requestCancellationNonActiveFails() {
        long sellerSn = insertUser("t_cancel_seller");
        long aucSn = insertAuction(sellerSn, AuctionStatusCode.ENDED, BigDecimal.valueOf(10000));

        assertThatThrownBy(() -> cancellationService.requestCancellation(aucSn, sellerSn, "종료 경매 취소"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("중복 처리 대기 취소 요청은 실패한다")
    void duplicatePendingRequestFails() {
        long sellerSn = insertUser("t_cancel_seller");
        long aucSn = insertActiveAuction(sellerSn);
        cancellationService.requestCancellation(aucSn, sellerSn, "1차 요청");

        assertThatThrownBy(() -> cancellationService.requestCancellation(aucSn, sellerSn, "2차 요청"))
                .isInstanceOf(CustomException.class);
        assertThat(pendingCount(aucSn)).isEqualTo(1);
    }

    @Test
    @DisplayName("취소요청 중 입찰은 실패한다")
    void bidFailsWhileCancelRequested() {
        long sellerSn = insertUser("t_cancel_seller");
        long bidderSn = insertUser("t_cancel_bidder");
        long aucSn = insertActiveAuction(sellerSn);
        cancellationService.requestCancellation(aucSn, sellerSn, "판매 불가");

        AuctionBidRequest request = new AuctionBidRequest();
        request.setBidAmount(BigDecimal.valueOf(20000));

        assertThatThrownBy(() -> auctionService.placeBid(aucSn, bidderSn, request))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("취소요청 중 즉시구매는 실패한다")
    void buyNowFailsWhileCancelRequested() {
        long sellerSn = insertUser("t_cancel_seller");
        long bidderSn = insertUser("t_cancel_bidder");
        long aucSn = insertActiveAuction(sellerSn);
        cancellationService.requestCancellation(aucSn, sellerSn, "판매 불가");

        assertThatThrownBy(() -> auctionService.buyNow(aucSn, bidderSn, new AuctionBuyNowRequest()))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("정상 승인: 취소요청 경매가 취소로 확정되고 최고입찰은 예외취소·홀딩 반환된다")
    void approveCancellation() {
        long sellerSn = insertUser("t_cancel_seller");
        long bidderSn = insertUser("t_cancel_bidder");
        long adminSn = insertUser("t_cancel_admin");
        long aucSn = insertActiveAuction(sellerSn);
        long bidSn = insertBid(aucSn, bidderSn, BigDecimal.valueOf(20000), BidStatusCode.HIGHEST);
        creditAvailable(bidderSn, 100000);
        pointService.hold(bidderSn, 20000, RefType.BID, bidSn, "입찰 홀딩");
        cancellationService.requestCancellation(aucSn, sellerSn, "판매 불가");
        long reqSn = cancelRequestSn(aucSn);

        cancellationService.approveCancellation(reqSn, adminSn, "승인");

        assertThat(auctionStatus(aucSn)).isEqualTo(AuctionStatusCode.CANCELED);
        assertThat(cancelApprovalYn(reqSn)).isEqualTo("Y");
        assertThat(bidStatus(bidSn)).isEqualTo(BidStatusCode.EXCEPTION_CANCELED);
        PointBalance balance = pointService.getBalance(bidderSn);
        assertThat(balance.getAvailableAmt()).isEqualTo(100000);
        assertThat(balance.getHoldAmt()).isZero();
    }

    @Test
    @DisplayName("정상 반려: 취소요청 경매가 진행 상태로 복귀한다")
    void rejectCancellation() {
        long sellerSn = insertUser("t_cancel_seller");
        long adminSn = insertUser("t_cancel_admin");
        long aucSn = insertActiveAuction(sellerSn);
        cancellationService.requestCancellation(aucSn, sellerSn, "판매 불가");
        long reqSn = cancelRequestSn(aucSn);

        cancellationService.rejectCancellation(reqSn, adminSn, "사유 부족");

        assertThat(auctionStatus(aucSn)).isEqualTo(AuctionStatusCode.ACTIVE);
        assertThat(cancelApprovalYn(reqSn)).isEqualTo("N");
    }

    @Test
    @DisplayName("이미 처리된 요청의 재승인·재반려는 실패한다")
    void alreadyProcessedFails() {
        long sellerSn = insertUser("t_cancel_seller");
        long adminSn = insertUser("t_cancel_admin");
        long aucSn = insertActiveAuction(sellerSn);
        cancellationService.requestCancellation(aucSn, sellerSn, "판매 불가");
        long reqSn = cancelRequestSn(aucSn);
        cancellationService.rejectCancellation(reqSn, adminSn, "사유 부족");

        assertThatThrownBy(() -> cancellationService.approveCancellation(reqSn, adminSn, "승인"))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> cancellationService.rejectCancellation(reqSn, adminSn, "반려"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("승인과 반려 동시 요청 중 하나만 성공한다")
    void concurrentApproveAndRejectOnlyOneSucceeds() throws InterruptedException {
        long sellerSn = insertUser("t_cancel_seller");
        long adminSn = insertUser("t_cancel_admin");
        long aucSn = insertActiveAuction(sellerSn);
        cancellationService.requestCancellation(aucSn, sellerSn, "판매 불가");
        long reqSn = cancelRequestSn(aucSn);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        pool.submit(() -> runConcurrent(ready, go, successCount, failCount,
                () -> cancellationService.approveCancellation(reqSn, adminSn, "승인")));
        pool.submit(() -> runConcurrent(ready, go, successCount, failCount,
                () -> cancellationService.rejectCancellation(reqSn, adminSn, "반려")));

        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        pool.shutdown();

        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);
        assertThat(cancelApprovalYn(reqSn)).isIn("Y", "N");
    }

    @Test
    @DisplayName("반려 사유 공백은 실패한다")
    void blankRejectReasonFails() {
        long sellerSn = insertUser("t_cancel_seller");
        long adminSn = insertUser("t_cancel_admin");
        long aucSn = insertActiveAuction(sellerSn);
        cancellationService.requestCancellation(aucSn, sellerSn, "판매 불가");
        long reqSn = cancelRequestSn(aucSn);

        assertThatThrownBy(() -> cancellationService.rejectCancellation(reqSn, adminSn, " "))
                .isInstanceOf(CustomException.class);
    }

    private void runConcurrent(CountDownLatch ready, CountDownLatch go,
                               AtomicInteger successCount, AtomicInteger failCount, Runnable action) {
        ready.countDown();
        try {
            go.await();
            action.run();
            successCount.incrementAndGet();
        } catch (Exception e) {
            failCount.incrementAndGet();
        }
    }

    private long insertUser(String prefix) {
        String loginId = prefix + "_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, prefix, loginId + "@test.local");
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        userIds.add(id);
        return id;
    }

    private long insertActiveAuction(long sellerSn) {
        return insertAuction(sellerSn, AuctionStatusCode.ACTIVE, BigDecimal.valueOf(10000));
    }

    private long insertAuction(long sellerSn, String statusCode, BigDecimal currentAmount) {
        long prdSn = insertProduct(sellerSn);
        jdbc.update("""
                INSERT INTO AUCTION (
                    PRD_SN,
                    AUC_STATUS_CD,
                    AUC_CUR_AMT,
                    AUC_BID_UNIT_AMT,
                    AUC_START_DT,
                    AUC_END_DT,
                    AUC_EXT_CNT
                )
                VALUES (?, ?, ?, 1000, ?, ?, 0)
                """,
                prdSn,
                statusCode,
                currentAmount,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1));
        long aucSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        auctionIds.add(aucSn);
        return aucSn;
    }

    private long insertProduct(long sellerSn) {
        jdbc.update("""
                INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_STATUS_CD, PRD_START_AMT, PRD_TRD_METHOD_CD)
                VALUES (?, 2, '테스트 취소요청 상품', 'PRDC0002', 10000,
                        (SELECT C.CMM_CD FROM CMM_CODE C
                         JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'TRDG03' ORDER BY C.CMM_SORT_NO LIMIT 1))
                """, sellerSn);
        long prdSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        productIds.add(prdSn);
        return prdSn;
    }

    private long insertBid(long aucSn, long bidderSn, BigDecimal amount, String statusCode) {
        jdbc.update("""
                INSERT INTO BID (AUC_SN, USR_SN, BID_AMT, BID_STATUS_CD)
                VALUES (?, ?, ?, ?)
                """, aucSn, bidderSn, amount, statusCode);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
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

    private String auctionStatus(long aucSn) {
        return jdbc.queryForObject("SELECT AUC_STATUS_CD FROM AUCTION WHERE AUC_SN = ?", String.class, aucSn);
    }

    private String bidStatus(long bidSn) {
        return jdbc.queryForObject("SELECT BID_STATUS_CD FROM BID WHERE BID_SN = ?", String.class, bidSn);
    }

    private int pendingCount(long aucSn) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM AUCTION_CANCEL_REQUEST
                WHERE AUC_SN = ?
                  AND AUC_CNL_REQ_APRV_YN IS NULL
                """, Integer.class, aucSn);
    }

    private long cancelRequestSn(long aucSn) {
        return jdbc.queryForObject("""
                SELECT AUC_CNL_REQ_SN
                FROM AUCTION_CANCEL_REQUEST
                WHERE AUC_SN = ?
                ORDER BY AUC_CNL_REQ_SN DESC
                LIMIT 1
                """, Long.class, aucSn);
    }

    private String cancelApprovalYn(long reqSn) {
        return jdbc.queryForObject("""
                SELECT AUC_CNL_REQ_APRV_YN
                FROM AUCTION_CANCEL_REQUEST
                WHERE AUC_CNL_REQ_SN = ?
                """, String.class, reqSn);
    }

    private String joinIds(List<Long> ids) {
        return String.join(",", ids.stream().map(String::valueOf).toList());
    }

    private void ensureSchema() {
        jdbc.update("""
                INSERT INTO CMM_CODE
                    (CMM_PARENT_SN, CMM_CD, CMM_NM, CMM_EXPLN, CMM_SORT_NO, CMM_USE_YN, CMM_REG_ID, CMM_UPDT_ID)
                SELECT parent.CMM_SN, 'AUCC0006', '취소요청',
                       '판매자가 취소 사유를 제출하여 관리자 승인 또는 반려를 기다리는 상태',
                       60, 'Y', 'TEST', 'TEST'
                  FROM CMM_CODE parent
                 WHERE parent.CMM_CD = 'AUCG01'
                   AND NOT EXISTS (SELECT 1 FROM CMM_CODE existing WHERE existing.CMM_CD = 'AUCC0006')
                """);
        jdbc.update("""
                UPDATE CMM_CODE
                   SET CMM_NM = '취소',
                       CMM_EXPLN = '관리자 승인 또는 운영 조치로 확정된 경매 취소',
                       CMM_USE_YN = 'Y',
                       CMM_UPDT_ID = 'TEST'
                 WHERE CMM_CD = 'AUCC0005'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS AUCTION_CANCEL_REQUEST (
                    AUC_CNL_REQ_SN bigint NOT NULL AUTO_INCREMENT,
                    AUC_SN bigint NOT NULL,
                    REQ_USR_SN bigint NOT NULL,
                    AUC_CNL_REQ_RSN_CN varchar(1000) NOT NULL,
                    AUC_CNL_REQ_APRV_YN char(1) NULL,
                    PROC_USR_SN bigint NULL,
                    AUC_CNL_REQ_PROC_RSN_CN varchar(1000) NULL,
                    AUC_CNL_REQ_PROC_DT datetime NULL,
                    AUC_CNL_REQ_REG_DT datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    AUC_CNL_REQ_UPDT_DT datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    AUC_CNL_REQ_REG_ID varchar(50) NOT NULL DEFAULT 'SYSTEM',
                    AUC_CNL_REQ_UPDT_ID varchar(50) NOT NULL DEFAULT 'SYSTEM',
                    PENDING_AUC_SN bigint GENERATED ALWAYS AS (
                        CASE WHEN AUC_CNL_REQ_APRV_YN IS NULL THEN AUC_SN ELSE NULL END
                    ) STORED,
                    PRIMARY KEY (AUC_CNL_REQ_SN),
                    UNIQUE KEY UK_AUC_CNL_REQ_PENDING (PENDING_AUC_SN),
                    KEY IX_AUC_CNL_REQ_AUC_SN (AUC_SN),
                    KEY IX_AUC_CNL_REQ_REQ_USR_SN (REQ_USR_SN),
                    KEY IX_AUC_CNL_REQ_PROC_USR_SN (PROC_USR_SN),
                    CONSTRAINT FK_AUC_CNL_REQ_AUC FOREIGN KEY (AUC_SN) REFERENCES AUCTION (AUC_SN),
                    CONSTRAINT FK_AUC_CNL_REQ_REQ_USR FOREIGN KEY (REQ_USR_SN) REFERENCES USERS (USR_SN),
                    CONSTRAINT FK_AUC_CNL_REQ_PROC_USR FOREIGN KEY (PROC_USR_SN) REFERENCES USERS (USR_SN),
                    CONSTRAINT CK_AUC_CNL_REQ_APRV_YN CHECK (
                        AUC_CNL_REQ_APRV_YN IN ('Y', 'N') OR AUC_CNL_REQ_APRV_YN IS NULL
                    )
                )
                """);
    }
}
