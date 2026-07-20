package nct.auction;

import static org.assertj.core.api.Assertions.assertThat;

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
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, prefix, loginId + "@test.local");
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        userIds.add(id);
        return id;
    }

    private long insertProduct(long sellerSn, BigDecimal instantBuyAmount) {
        jdbc.update("""
                INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_STATUS_CD, PRD_START_AMT, PRD_IBY_AMT, PRD_TRD_METHOD_CD)
                VALUES (?, 2, '동시성 테스트 경매 상품', 'PRDC0002', 10000, ?,
                        (SELECT C.CMM_CD FROM CMM_CODE C
                         JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'TRDG03' ORDER BY C.CMM_SORT_NO LIMIT 1))
                """, sellerSn, instantBuyAmount);
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        productIds.add(id);
        return id;
    }

    private long insertAuction(long prdSn, BigDecimal currentAmount) {
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
                LocalDateTime.now().plusHours(1));
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        auctionIds.add(id);
        return id;
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
