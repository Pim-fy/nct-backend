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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import nct.auction.dto.AuctionBidRequest;
import nct.auction.dto.AuctionBuyNowRequest;
import nct.auction.service.AuctionService;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.point.domain.PointBalance;
import nct.point.service.PointService;

@SpringBootTest
class AuctionConcurrencyTest {

    @Autowired AuctionService auctionService;
    @Autowired PointService pointService;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> auctionIds = new ArrayList<>();
    private final List<Long> productIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        List<Long> tradeIds = tradeIdsByProducts();
        deleteIn("CHAT_ROOM", "TRD_SN", tradeIds);
        deleteIn("TRADE_DELIVERY", "TRD_SN", tradeIds);
        deleteIn("TRADE_STATUS_HIST", "TRD_SN", tradeIds);
        deleteIn("TRADE", "TRD_SN", tradeIds);
        if (!auctionIds.isEmpty()) {
            deleteIn("POINT_LEDGER", "PT_LDG_REF_SN", bidIdsByAuctions());
            deleteIn("BID", "AUC_SN", auctionIds);
            deleteIn("AUCTION", "AUC_SN", auctionIds);
        }
        deleteIn("PRODUCT", "PRD_SN", productIds);
        deleteIn("POINT_LEDGER", "USR_SN", userIds);
        deleteIn("NOTIFICATION", "USR_SN", userIds);
        deleteIn("USERS", "USR_SN", userIds);
    }

    @Test
    @DisplayName("입찰과 즉시구매가 동시에 들어와도 즉시구매가 경매를 종료한다")
    void concurrentBidAndBuyNowClosesByBuyNow() throws InterruptedException {
        long sellerSn = insertUser("t_auc_con_seller");
        long bidderSn = insertUser("t_auc_con_bidder");
        long buyerSn = insertUser("t_auc_con_buyer");
        long prdSn = insertProduct(sellerSn, BigDecimal.valueOf(30000));
        long aucSn = insertAuction(prdSn, BigDecimal.valueOf(10000));
        creditAvailable(bidderSn, 50000);
        creditAvailable(buyerSn, 50000);

        AuctionBidRequest bidRequest = new AuctionBidRequest();
        bidRequest.setBidAmount(BigDecimal.valueOf(12000));

        AtomicInteger bidSuccessCount = new AtomicInteger();
        AtomicInteger buyNowSuccessCount = new AtomicInteger();
        runConcurrently(
                () -> {
                    auctionService.placeBid(aucSn, bidderSn, bidRequest);
                    bidSuccessCount.incrementAndGet();
                },
                () -> {
                    auctionService.buyNow(aucSn, buyerSn, new AuctionBuyNowRequest());
                    buyNowSuccessCount.incrementAndGet();
                });

        assertThat(buyNowSuccessCount.get()).isEqualTo(1);
        assertThat(bidSuccessCount.get()).isBetween(0, 1);
        assertThat(auctionStatus(aucSn)).isEqualTo("AUCC0003");
        assertThat(auctionCurrentAmount(aucSn)).isEqualByComparingTo("30000");
        assertThat(pointService.getBalance(bidderSn).getHoldAmt()).isZero();

        PointBalance buyerBalance = pointService.getBalance(buyerSn);
        assertThat(buyerBalance.getAvailableAmt()).isEqualTo(20000);
        assertThat(buyerBalance.getHoldAmt()).isZero();
        assertThat(materialTradeCount(prdSn)).isEqualTo(1);
    }

    @Test
    @DisplayName("즉시구매 동시 요청은 한 건만 성공한다")
    void concurrentBuyNowOnlyOneSucceeds() throws InterruptedException {
        long sellerSn = insertUser("t_auc_con_seller");
        long firstBuyerSn = insertUser("t_auc_con_first_buyer");
        long secondBuyerSn = insertUser("t_auc_con_second_buyer");
        long prdSn = insertProduct(sellerSn, BigDecimal.valueOf(30000));
        long aucSn = insertAuction(prdSn, BigDecimal.valueOf(10000));
        creditAvailable(firstBuyerSn, 50000);
        creditAvailable(secondBuyerSn, 50000);

        AtomicInteger successCount = new AtomicInteger();
        runConcurrently(
                () -> {
                    auctionService.buyNow(aucSn, firstBuyerSn, new AuctionBuyNowRequest());
                    successCount.incrementAndGet();
                },
                () -> {
                    auctionService.buyNow(aucSn, secondBuyerSn, new AuctionBuyNowRequest());
                    successCount.incrementAndGet();
                });

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(auctionStatus(aucSn)).isEqualTo("AUCC0003");
        assertThat(auctionCurrentAmount(aucSn)).isEqualByComparingTo("30000");
        assertThat(highestBidCount(aucSn)).isEqualTo(1);
        assertThat(pointService.getBalance(firstBuyerSn).getHoldAmt()).isZero();
        assertThat(pointService.getBalance(secondBuyerSn).getHoldAmt()).isZero();
        assertThat(materialTradeCount(prdSn)).isEqualTo(1);
    }

    @Test
    @DisplayName("만료 경매 마감이 동시에 실행되어도 낙찰은 한 번만 확정된다")
    void concurrentFinalizationOnlyOneSucceeds() throws InterruptedException {
        long sellerSn = insertUser("t_auc_con_seller");
        long bidderSn = insertUser("t_auc_con_bidder");
        long prdSn = insertProduct(sellerSn, null);
        long aucSn = insertAuction(
                prdSn,
                BigDecimal.valueOf(12000),
                databaseNow().minusMinutes(1));
        long bidSn = insertBid(aucSn, bidderSn, BigDecimal.valueOf(12000));
        creditAvailable(bidderSn, 50000);
        pointService.hold(bidderSn, 12000, RefType.BID, bidSn, "동시 마감 테스트 홀딩");

        AtomicInteger successCount = new AtomicInteger();
        runConcurrently(
                () -> {
                    if (auctionService.finalizeExpiredAuction(aucSn)) {
                        successCount.incrementAndGet();
                    }
                },
                () -> {
                    if (auctionService.finalizeExpiredAuction(aucSn)) {
                        successCount.incrementAndGet();
                    }
                });

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(auctionStatus(aucSn)).isEqualTo("AUCC0003");
        assertThat(escrowLedgerCount(bidderSn, bidSn)).isEqualTo(1);
        assertThat(materialTradeCount(prdSn)).isEqualTo(1);
    }

    @Test
    @DisplayName("배송지 스냅샷 생성 실패 시 즉시구매 전체 처리를 롤백한다")
    void buyNowRollsBackWhenTradeCreationFails() {
        long sellerSn = insertUser("t_auc_tx_seller");
        long buyerSn = insertUser("t_auc_tx_buyer");
        jdbc.update("UPDATE USERS SET USR_ADDR = NULL, USR_DADDR = NULL, USR_ZIP = NULL WHERE USR_SN = ?", buyerSn);
        long prdSn = insertProduct(sellerSn, BigDecimal.valueOf(30000));
        long aucSn = insertAuction(prdSn, BigDecimal.valueOf(10000));
        creditAvailable(buyerSn, 50000);

        assertThatThrownBy(() -> auctionService.buyNow(aucSn, buyerSn, new AuctionBuyNowRequest()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        assertThat(auctionStatus(aucSn)).isEqualTo("AUCC0002");
        assertThat(auctionCurrentAmount(aucSn)).isEqualByComparingTo("10000");
        assertThat(highestBidCount(aucSn)).isZero();
        assertThat(materialTradeCount(prdSn)).isZero();
        assertThat(pointService.getBalance(buyerSn).getAvailableAmt()).isEqualTo(50000);
        assertThat(pointService.getBalance(buyerSn).getHoldAmt()).isZero();
    }

    @Test
    @DisplayName("배송지 스냅샷 생성 실패 시 자동 낙찰 전체 처리를 롤백한다")
    void finalizationRollsBackWhenTradeCreationFails() {
        long sellerSn = insertUser("t_auc_tx_seller");
        long bidderSn = insertUser("t_auc_tx_bidder");
        jdbc.update("UPDATE USERS SET USR_ADDR = NULL, USR_DADDR = NULL, USR_ZIP = NULL WHERE USR_SN = ?", bidderSn);
        long prdSn = insertProduct(sellerSn, null);
        long aucSn = insertAuction(
                prdSn,
                BigDecimal.valueOf(12000),
                databaseNow().minusMinutes(1));
        long bidSn = insertBid(aucSn, bidderSn, BigDecimal.valueOf(12000));
        creditAvailable(bidderSn, 50000);
        pointService.hold(bidderSn, 12000, RefType.BID, bidSn, "롤백 테스트 홀딩");

        assertThatThrownBy(() -> auctionService.finalizeExpiredAuction(aucSn))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        assertThat(auctionStatus(aucSn)).isEqualTo("AUCC0002");
        assertThat(materialTradeCount(prdSn)).isZero();
        assertThat(pointService.getBalance(bidderSn).getAvailableAmt()).isEqualTo(38000);
        assertThat(pointService.getBalance(bidderSn).getHoldAmt()).isEqualTo(12000);
        assertThat(escrowLedgerCount(bidderSn, bidSn)).isZero();
    }

    private void runConcurrently(Runnable first, Runnable second) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        pool.submit(() -> runConcurrent(ready, go, first));
        pool.submit(() -> runConcurrent(ready, go, second));

        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        pool.shutdown();

        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    private void runConcurrent(CountDownLatch ready, CountDownLatch go, Runnable action) {
        ready.countDown();
        try {
            go.await();
            action.run();
        } catch (Exception ignored) {
            // 실패한 쪽은 경합에서 진 요청이다. 최종 DB 상태로 정책을 검증한다.
        }
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
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        userIds.add(id);
        return id;
    }

    private long insertProduct(long sellerSn, BigDecimal instantBuyAmount) {
        jdbc.update("""
                INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_STATUS_CD, PRD_START_AMT, PRD_IBY_AMT, PRD_TRD_METHOD_CD)
                VALUES (?, 2, '동시성 테스트 경매 상품', 'PRDC0002', 10000, ?, 'TRDC0009')
                """, sellerSn, instantBuyAmount);
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        productIds.add(id);
        return id;
    }

    private long insertAuction(long prdSn, BigDecimal currentAmount) {
        return insertAuction(prdSn, currentAmount, LocalDateTime.now().plusHours(1));
    }

    private long insertAuction(long prdSn, BigDecimal currentAmount, LocalDateTime endDateTime) {
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
                VALUES (?, 'AUCC0002', ?, 1000, ?, ?, 0)
                """,
                prdSn,
                currentAmount,
                LocalDateTime.now().minusHours(1),
                endDateTime);
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        auctionIds.add(id);
        return id;
    }

    private long insertBid(long aucSn, long bidderSn, BigDecimal amount) {
        jdbc.update("""
                INSERT INTO BID (AUC_SN, USR_SN, BID_AMT, BID_STATUS_CD)
                VALUES (?, ?, ?, 'BIDC0001')
                """, aucSn, bidderSn, amount);
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
                VALUES (?, 'PTLC0001', 'PTLC0004', ?, ?, '동시성 테스트 충전')
                """, usrSn, amount, amount);
    }

    private String auctionStatus(long aucSn) {
        return jdbc.queryForObject("SELECT AUC_STATUS_CD FROM AUCTION WHERE AUC_SN = ?", String.class, aucSn);
    }

    private BigDecimal auctionCurrentAmount(long aucSn) {
        return jdbc.queryForObject("SELECT AUC_CUR_AMT FROM AUCTION WHERE AUC_SN = ?", BigDecimal.class, aucSn);
    }

    private int highestBidCount(long aucSn) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM BID WHERE AUC_SN = ? AND BID_STATUS_CD = 'BIDC0001'",
                Integer.class,
                aucSn);
    }

    private int escrowLedgerCount(long usrSn, long bidSn) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM POINT_LEDGER
                WHERE USR_SN = ?
                  AND PT_LDG_TYPE_CD = 'PTLC0007'
                  AND PT_LDG_REF_TYPE_CD = ?
                  AND PT_LDG_REF_SN = ?
                """, Integer.class, usrSn, RefType.BID.getCode(), bidSn);
    }

    private LocalDateTime databaseNow() {
        return jdbc.queryForObject("SELECT NOW()", LocalDateTime.class);
    }

    private int materialTradeCount(long prdSn) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM TRADE
                WHERE PRD_SN = ?
                  AND TRD_TYPE_CD = 'TRDC0001'
                """, Integer.class, prdSn);
    }

    private List<Long> tradeIdsByProducts() {
        if (productIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", productIds.stream().map(id -> "?").toList());
        return jdbc.queryForList(
                "SELECT TRD_SN FROM TRADE WHERE PRD_SN IN (" + placeholders + ")",
                Long.class,
                productIds.toArray());
    }

    private List<Long> bidIdsByAuctions() {
        if (auctionIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", auctionIds.stream().map(id -> "?").toList());
        return jdbc.queryForList(
                "SELECT BID_SN FROM BID WHERE AUC_SN IN (" + placeholders + ")",
                Long.class,
                auctionIds.toArray());
    }

    private void deleteIn(String table, String column, List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        jdbc.update("DELETE FROM " + table + " WHERE " + column + " IN (" + placeholders + ")", ids.toArray());
    }
}
