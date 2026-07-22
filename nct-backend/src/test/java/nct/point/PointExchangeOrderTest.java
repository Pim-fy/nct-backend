package nct.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import nct.common.domain.RefType;
import nct.notification.service.NotificationService;
import nct.point.domain.PointExchangeOrderStatus;
import nct.point.dto.PointExchangeOrderResponse;
import nct.point.exception.PointException;
import nct.point.service.PointExchangeService;
import nct.point.service.PointService;

/**
 * Claude Code 작성 (BJN, 2026-07-17)
 *
 * [테스트 - 포인트 환전 신청·이력 조회] (F-PAY-012, D-026)
 *
 * 공유 DB(NCTDB) 주의사항 — PointChargeOrderTest와 동일:
 * - @Transactional 테스트는 메소드 종료 시 전부 롤백되어 행을 남기지 않는다
 * - 테스트 회원은 매 실행 nanoTime으로 유니크하게 생성되어 팀원 데이터와 충돌하지 않는다
 *
 * 환전 가능 잔액은 정산가능 버킷이므로, 테스트마다 creditSettleable로 잔액을 만들어 두고 검증한다.
 */
@SpringBootTest
@Transactional
class PointExchangeOrderTest {

    @Autowired PointExchangeService pointExchangeService;
    @Autowired PointService pointService;
    @Autowired NotificationService notificationService;
    @Autowired JdbcTemplate jdbc;

    long usrSn;

    @BeforeEach
    void setUpUser() {
        String loginId = "t_exc_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD,
                                   USR_BANK_NM, USR_ACNT_NO)
                VALUES (?, '{noop}test', ?, ?, 'USRC0001', 'ROLE_USER', '국민은행', '123-45-6789')
                """, loginId, loginId, loginId + "@test.local");
        usrSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    @DisplayName("신청 성공: 즉시 차감·계좌 스냅샷·접수 알림까지 한 번에 기록된다")
    void applySuccess() {
        // 판매대금으로 정산가능 50,000P를 만들어 둔다
        pointService.creditSettleable(usrSn, 50_000, RefType.TRADE, 1L, "테스트 정산");

        pointExchangeService.apply(usrSn, 30_000);

        // ① 신청 행: 신청 상태 + 계좌 스냅샷 + 차감 원장 연결
        var orders = pointExchangeService.getOrderList(usrSn);
        assertThat(orders).singleElement().satisfies(o -> {
            assertThat(o.getPtExcOrdAmt()).isEqualTo(30_000);
            assertThat(o.getPtExcOrdStatusCd()).isEqualTo(PointExchangeOrderStatus.REQUESTED.getCode());
            assertThat(o.getStatusNm()).isEqualTo("신청"); // 기초데이터 PEOC0001='신청'
            assertThat(o.getPtExcOrdBankNm()).isEqualTo("국민은행");
            assertThat(o.getPtExcOrdAcntNo()).isEqualTo("123-45-6789");
            assertThat(o.getPtExcOrdDeductLdgSn()).isNotNull(); // 차감 원장과 연결됨
        });

        // ② 잔액: 정산가능 50,000 → 20,000 (즉시 차감)
        assertThat(pointService.getBalance(usrSn).getSettleableAmt()).isEqualTo(20_000);

        // ③ 접수 알림이 같은 트랜잭션에서 기록됨
        assertThat(notificationService.getList(usrSn))
                .anySatisfy(n -> assertThat(n.getNtfTtl()).isEqualTo("환전 신청 접수"));

        // ④ 응답 DTO 변환 — 프론트 계약대로 채워지는지
        PointExchangeOrderResponse dto = PointExchangeOrderResponse.from(orders.get(0));
        assertThat(dto.getAmount()).isEqualTo(30_000);
        assertThat(dto.getStatus()).isEqualTo("신청");
        assertThat(dto.getBankName()).isEqualTo("국민은행");
        assertThat(dto.getRejectReason()).isNull();
    }

    @Test
    @DisplayName("잔액 초과: 환전 가능(정산가능) 잔액보다 큰 신청은 거부되고 아무 기록도 남지 않는다")
    void applyOverBalance() {
        pointService.creditSettleable(usrSn, 10_000, RefType.TRADE, 1L, "테스트 정산");

        assertThatThrownBy(() -> pointExchangeService.apply(usrSn, 20_000))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("부족");

        assertThat(pointExchangeService.getOrderList(usrSn)).isEmpty();
        assertThat(pointService.getBalance(usrSn).getSettleableAmt()).isEqualTo(10_000); // 차감 안 됨
    }

    @Test
    @DisplayName("계좌 미등록: 신청이 차단되고 포인트도 차감되지 않는다")
    void applyWithoutAccount() {
        pointService.creditSettleable(usrSn, 50_000, RefType.TRADE, 1L, "테스트 정산");
        // 계좌를 비운다 (마이페이지에서 아직 등록 안 한 회원 상황)
        jdbc.update("UPDATE USERS SET USR_BANK_NM = NULL, USR_ACNT_NO = NULL WHERE USR_SN = ?", usrSn);

        assertThatThrownBy(() -> pointExchangeService.apply(usrSn, 30_000))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("계좌");

        assertThat(pointExchangeService.getOrderList(usrSn)).isEmpty();
        assertThat(pointService.getBalance(usrSn).getSettleableAmt()).isEqualTo(50_000); // 차감 안 됨
    }

    @Test
    @DisplayName("금액 검증: 0 이하 신청은 거부된다")
    void applyNonPositiveAmount() {
        assertThatThrownBy(() -> pointExchangeService.apply(usrSn, 0))
                .isInstanceOf(PointException.class);
        assertThatThrownBy(() -> pointExchangeService.apply(usrSn, -1_000))
                .isInstanceOf(PointException.class);

        assertThat(pointExchangeService.getOrderList(usrSn)).isEmpty();
    }

    // ---------- 관리자 처리 (지급 완료 / 반려) ----------

    @Test
    @DisplayName("관리자 지급 완료: 상태·처리자 기록 + 지급 완료 알림 (포인트는 신청 때 이미 차감)")
    void adminComplete() {
        pointService.creditSettleable(usrSn, 50_000, RefType.TRADE, 1L, "테스트 정산");
        long ordSn = pointExchangeService.apply(usrSn, 30_000);

        pointExchangeService.complete(ordSn, usrSn); // 처리자는 아무 회원이나 가능(FK) — 테스트 편의상 본인

        var order = pointExchangeService.getOrderList(usrSn).get(0);
        assertThat(order.getPtExcOrdStatusCd()).isEqualTo(PointExchangeOrderStatus.COMPLETED.getCode());
        assertThat(order.getPtExcOrdProcUsrSn()).isEqualTo(usrSn);
        assertThat(order.getPtExcOrdProcDt()).isNotNull();
        // 잔액은 신청 때 차감된 그대로 (완료 처리로 추가 변동 없음)
        assertThat(pointService.getBalance(usrSn).getSettleableAmt()).isEqualTo(20_000);
        assertThat(notificationService.getList(usrSn))
                .anySatisfy(n -> assertThat(n.getNtfTtl()).isEqualTo("환전 지급 완료"));
    }

    @Test
    @DisplayName("관리자 반려: 복원 원장이 짝으로 기록되고 잔액이 원상복구된다")
    void adminReject() {
        pointService.creditSettleable(usrSn, 50_000, RefType.TRADE, 1L, "테스트 정산");
        long ordSn = pointExchangeService.apply(usrSn, 30_000);
        assertThat(pointService.getBalance(usrSn).getSettleableAmt()).isEqualTo(20_000); // 차감 확인

        pointExchangeService.reject(ordSn, usrSn, "계좌 정보 불일치");

        var order = pointExchangeService.getOrderList(usrSn).get(0);
        assertThat(order.getPtExcOrdStatusCd()).isEqualTo(PointExchangeOrderStatus.REJECTED.getCode());
        assertThat(order.getPtExcOrdRestoreLdgSn()).isNotNull(); // 복원 원장 연결
        assertThat(order.getPtExcOrdRjctRsnCn()).isEqualTo("계좌 정보 불일치");
        // 복원은 항상 사용가능 버킷으로 간다(차감이 항상 사용가능에서 이루어지므로) — 총 보유는 원상복구
        var bal = pointService.getBalance(usrSn);
        assertThat(bal.getSettleableAmt()).isEqualTo(20_000); // 신청 시 전환된 그대로, 복원으로 안 돌아감
        assertThat(bal.getAvailableAmt()).isEqualTo(30_000); // 차감분이 복원됨
        assertThat(bal.getTotalAmt()).isEqualTo(50_000);
        assertThat(notificationService.getList(usrSn))
                .anySatisfy(n -> assertThat(n.getNtfTtl()).isEqualTo("환전 신청 반려"));
    }

    // ---------- 사용가능 포인트 환전 대상 확대 (2026-07-22 사용자 결정) ----------

    @Test
    @DisplayName("사용가능 포인트만으로도 환전 신청이 성공한다 — 정산가능 0이어도 무관")
    void applyWithAvailableOnly() {
        jdbc.update("""
                INSERT INTO POINT_LEDGER (USR_SN, PT_LDG_PT_TYPE_CD, PT_LDG_TYPE_CD, PT_LDG_AMT, PT_LDG_BAL_AFTER_AMT, PT_LDG_RSN_CN)
                VALUES (?, 'PTLC0001', 'PTLC0004', 100000, 100000, '테스트 충전')
                """, usrSn);

        long ordSn = pointExchangeService.apply(usrSn, 40_000);

        assertThat(pointExchangeService.getOrderList(usrSn)).singleElement()
                .satisfies(o -> assertThat(o.getPtExcOrdSn()).isEqualTo(ordSn));
        var bal = pointService.getBalance(usrSn);
        assertThat(bal.getAvailableAmt()).isEqualTo(60_000);
        assertThat(bal.getSettleableAmt()).isZero();
    }

    @Test
    @DisplayName("정산가능+사용가능 혼합 사용 — 사용가능을 먼저 다 쓰고 부족분만 정산가능이 채운다")
    void applyWithMixedBuckets() {
        // 정산가능 20,000 + 사용가능 10,000 보유, 25,000 신청
        // → 사용가능 10,000 전액 사용 + 정산가능에서 부족분 15,000만 전환·차감 (사용가능 우선 소진)
        pointService.creditSettleable(usrSn, 20_000, RefType.TRADE, 1L, "테스트 정산");
        jdbc.update("""
                INSERT INTO POINT_LEDGER (USR_SN, PT_LDG_PT_TYPE_CD, PT_LDG_TYPE_CD, PT_LDG_AMT, PT_LDG_BAL_AFTER_AMT, PT_LDG_RSN_CN)
                VALUES (?, 'PTLC0001', 'PTLC0004', 10000, 10000, '테스트 충전')
                """, usrSn);

        pointExchangeService.apply(usrSn, 25_000);

        var bal = pointService.getBalance(usrSn);
        assertThat(bal.getSettleableAmt()).isEqualTo(5_000); // 20,000 중 15,000만 전환되고 5,000 남음
        assertThat(bal.getAvailableAmt()).isZero(); // 10,000 + 15,000(전환) - 25,000(차감) = 0
    }

    @Test
    @DisplayName("정산가능분을 쓰는 신청은 진행 중인 거래 문제가 있으면 차단된다")
    void applyBlockedByDisputeWhenUsingSettleable() {
        pointService.creditSettleable(usrSn, 50_000, RefType.TRADE, 1L, "테스트 정산");
        insertDispute("TRDC0016"); // 접수 상태

        assertThatThrownBy(() -> pointExchangeService.apply(usrSn, 10_000))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("거래 문제");

        assertThat(pointExchangeService.getOrderList(usrSn)).isEmpty();
        assertThat(pointService.getBalance(usrSn).getSettleableAmt()).isEqualTo(50_000); // 차감 안 됨
    }

    @Test
    @DisplayName("사용가능만으로 충분한 신청은 거래 문제가 있어도 차단되지 않는다")
    void applyNotBlockedByDisputeWhenAvailableSuffices() {
        pointService.creditSettleable(usrSn, 50_000, RefType.TRADE, 1L, "테스트 정산");
        jdbc.update("""
                INSERT INTO POINT_LEDGER (USR_SN, PT_LDG_PT_TYPE_CD, PT_LDG_TYPE_CD, PT_LDG_AMT, PT_LDG_BAL_AFTER_AMT, PT_LDG_RSN_CN)
                VALUES (?, 'PTLC0001', 'PTLC0004', 100000, 100000, '테스트 충전')
                """, usrSn);
        insertDispute("TRDC0016"); // 접수 상태 — 하지만 정산가능을 안 건드리므로 무관해야 한다

        pointExchangeService.apply(usrSn, 30_000);

        var bal = pointService.getBalance(usrSn);
        assertThat(bal.getSettleableAmt()).isEqualTo(50_000); // 정산가능은 손대지 않음
        assertThat(bal.getAvailableAmt()).isEqualTo(70_000);
    }

    @Test
    @DisplayName("이중 처리 방지: 이미 처리된 신청은 완료/반려 모두 거부된다")
    void adminDoubleProcessRejected() {
        pointService.creditSettleable(usrSn, 50_000, RefType.TRADE, 1L, "테스트 정산");
        long ordSn = pointExchangeService.apply(usrSn, 30_000);
        pointExchangeService.complete(ordSn, usrSn);

        assertThatThrownBy(() -> pointExchangeService.complete(ordSn, usrSn))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("이미 처리된");
        assertThatThrownBy(() -> pointExchangeService.reject(ordSn, usrSn, "사유"))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("이미 처리된");

        // 반려 시도가 거부됐으니 복원도 일어나지 않았어야 한다
        assertThat(pointService.getBalance(usrSn).getSettleableAmt()).isEqualTo(20_000);
    }

    /** usrSn이 판매자인 거래에 지정 상태의 거래 문제를 건다 (PointConvertTest와 같은 방식) */
    private void insertDispute(String statusCd) {
        long counterpartSn = insertUser("t_exc_counterpart");

        jdbc.update("""
                INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_STATUS_CD, PRD_START_AMT, PRD_TRD_METHOD_CD)
                VALUES (?, 2, '환전 테스트 상품', 'PRDC0003', 10000,
                        (SELECT C.CMM_CD FROM CMM_CODE C
                         JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'TRDG03' ORDER BY C.CMM_SORT_NO LIMIT 1))
                """, usrSn);
        long prdSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("""
                INSERT INTO TRADE (TRD_TYPE_CD, TRD_STATUS_CD, TRD_AMT, SLLR_USR_SN, BYPR_USR_SN, PRD_SN)
                VALUES ('TRDC0001', 'TRDC0006', 10000, ?, ?, ?)
                """, usrSn, counterpartSn, prdSn);
        long trdSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("""
                INSERT INTO TRADE_DISPUTE (TRD_SN, DSPT_USR_SN, TRD_DSP_TYPE_CD, TRD_DSP_STATUS_CD, TRD_DSP_CN)
                VALUES (?, ?, 'TRDC0014', ?, '환전 차단 테스트용 거래 문제')
                """, trdSn, counterpartSn, statusCd);
    }

    private long insertUser(String prefix) {
        String loginId = prefix + "_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, prefix, loginId + "@test.local");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
