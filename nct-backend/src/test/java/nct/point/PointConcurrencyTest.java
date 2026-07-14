package nct.point;

import static org.assertj.core.api.Assertions.assertThat;

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

import nct.common.domain.RefType;
import nct.point.domain.PointBalance;
import nct.point.exception.InsufficientPointException;
import nct.point.service.PointService;

/**
 * [테스트 - 포인트 동시성 검증] (담당자6 도메인 계약)
 *
 * PointFlowTest와 달리 @Transactional을 걸지 않는다 — lockUser()의 SELECT ... FOR UPDATE가
 * 실제로 다른 스레드(=다른 커넥션·다른 트랜잭션)를 막아주는지 검증하려면 각 스레드가
 * 진짜로 커밋된 데이터를 놓고 경합해야 한다. 그래서 픽스처를 직접 커밋하고
 * @AfterEach에서 명시적으로 지운다 (공유 개발 DB에 잔여 데이터를 남기지 않기 위함).
 */
@SpringBootTest
class PointConcurrencyTest {

    @Autowired PointService pointService;
    @Autowired JdbcTemplate jdbc;

    long buyerSn;

    @BeforeEach
    void setUp() {
        String loginId = "t_concurrent_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', 'concurrent', ?, 'USRC0001', 'ROLE_USER')
                """, loginId, loginId + "@test.local");
        buyerSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("""
                INSERT INTO POINT_LEDGER (USR_SN, PT_LDG_PT_TYPE_CD, PT_LDG_TYPE_CD, PT_LDG_AMT, PT_LDG_BAL_AFTER_AMT, PT_LDG_RSN_CN)
                VALUES (?, 'PTLC0001', 'PTLC0004', 100000, 100000, '동시성 테스트 충전')
                """, buyerSn);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM POINT_LEDGER WHERE USR_SN = ?", buyerSn);
        jdbc.update("DELETE FROM USERS WHERE USR_SN = ?", buyerSn);
    }

    @Test
    @DisplayName("동시 홀딩: 사용가능 잔액을 초과하는 두 요청이 동시에 와도 한쪽만 성공한다 (이중 차감 방지)")
    void concurrentHoldDoesNotOverdraw() throws InterruptedException {
        // 잔액은 100,000인데 각각 60,000씩 서로 다른 참조 건으로 동시에 홀딩 시도
        // -> lockUser()의 행 잠금이 없다면 둘 다 "잔액 충분"으로 읽고 통과해 총 120,000이 빠져나가는 이중 차감이 발생한다
        int threadCount = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger insufficientCount = new AtomicInteger();

        Runnable task = () -> {
            long refSn = Thread.currentThread().threadId();
            ready.countDown();
            try {
                go.await();
                pointService.hold(buyerSn, 60000, RefType.BID, refSn, "동시성 테스트 홀딩");
                successCount.incrementAndGet();
            } catch (InsufficientPointException e) {
                insufficientCount.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        pool.submit(task);
        pool.submit(task);
        ready.await(5, TimeUnit.SECONDS);
        go.countDown(); // 두 스레드를 동시에 풀어준다
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(insufficientCount.get()).isEqualTo(1);

        PointBalance bal = pointService.getBalance(buyerSn);
        assertThat(bal.getAvailableAmt()).isEqualTo(40000); // 100,000 - 60,000, 음수로 내려가지 않음
        assertThat(bal.getHoldAmt()).isEqualTo(60000);

        List<Long> ledgerAmts = jdbc.queryForList(
                "SELECT PT_LDG_AMT FROM POINT_LEDGER WHERE USR_SN = ? AND PT_LDG_TYPE_CD = 'PTLC0005'",
                Long.class, buyerSn);
        assertThat(ledgerAmts).hasSize(2); // 성공한 홀딩 1건이 남긴 원장 2행(사용가능−, 홀딩+)
    }
}
