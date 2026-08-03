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
import nct.global.security.crypto.FieldCryptoService;
import nct.notification.service.NotificationService;
import nct.point.domain.PointBalance;
import nct.point.exception.DuplicateHoldException;
import nct.point.exception.InsufficientPointException;
import nct.point.exception.PointException;
import nct.point.service.PointService;
import nct.settlement.domain.SettlementStatus;
import nct.settlement.exception.SettlementException;
import nct.settlement.service.SettlementService;

/**
 * [테스트 - 포인트·정산·알림 흐름 통합 검증] (담당자6 도메인 계약)
 *
 * 공유 DB(NCTDB) 주의사항:
 * - @Transactional 테스트는 메소드 종료 시 전부 롤백되어 행을 남기지 않는다
 * - 단 AUTO_INCREMENT 번호는 롤백돼도 소모된다 (무해)
 * - 테스트 회원은 매 실행 nanoTime으로 유니크하게 생성되어 팀원 데이터와 충돌하지 않는다
 */
@SpringBootTest
@Transactional
class PointFlowTest {

    private static final long TEST_ADMIN_USR_SN = 700L;

    @Autowired PointService pointService;
    @Autowired SettlementService settlementService;
    @Autowired NotificationService notificationService;
    @Autowired JdbcTemplate jdbc;
    @Autowired FieldCryptoService fieldCryptoService;

    long buyerSn;
    long sellerSn;

    @BeforeEach
    void setUpUsers() {
        buyerSn = insertUser("t_buyer");
        sellerSn = insertUser("t_seller");
        // 시작 잔액은 원장 직접 적재 — 실충전(토스 승인)을 테스트에서 태울 수 없어서다
        jdbc.update("""
                INSERT INTO POINT_LEDGER (USR_SN, PT_LDG_PT_TYPE_CD, PT_LDG_TYPE_CD, PT_LDG_AMT, PT_LDG_BAL_AFTER_AMT, PT_LDG_RSN_CN)
                VALUES (?, 'PTLC0001', 'PTLC0004', 100000, 100000, '테스트 충전')
                """, buyerSn);
    }

    @Test
    @DisplayName("홀딩: 사용가능 차감, 홀딩 증가 (ML-PAY-002)")
    void hold() {
        pointService.hold(buyerSn, 30000, RefType.BID, 1L, "입찰 홀딩");

        PointBalance bal = pointService.getBalance(buyerSn);
        assertThat(bal.getAvailableAmt()).isEqualTo(70000);
        assertThat(bal.getHoldAmt()).isEqualTo(30000);
        assertThat(bal.getTotalAmt()).isEqualTo(100000);
    }

    @Test
    @DisplayName("홀딩: 잔액 부족 시 차단 (ML-PAY-001)")
    void holdInsufficient() {
        assertThatThrownBy(() -> pointService.hold(buyerSn, 200000, RefType.BID, 1L, "입찰 홀딩"))
                .isInstanceOf(InsufficientPointException.class);

        assertThat(pointService.getBalance(buyerSn).getAvailableAmt()).isEqualTo(100000);
    }

    @Test
    @DisplayName("홀딩: 같은 참조 건 중복 홀딩 차단 (ML-PAY-002)")
    void holdDuplicate() {
        pointService.hold(buyerSn, 30000, RefType.BID, 1L, "입찰 홀딩");

        assertThatThrownBy(() -> pointService.hold(buyerSn, 10000, RefType.BID, 1L, "중복 홀딩"))
                .isInstanceOf(DuplicateHoldException.class);
    }

    @Test
    @DisplayName("반환: 홀딩 전액 사용가능 복원 + 알림 생성 (ML-PAY-003)")
    void releaseHold() {
        pointService.hold(buyerSn, 30000, RefType.BID, 1L, "입찰 홀딩");

        long released = pointService.releaseHold(buyerSn, RefType.BID, 1L, "상위 입찰 발생");

        assertThat(released).isEqualTo(30000);
        PointBalance bal = pointService.getBalance(buyerSn);
        assertThat(bal.getAvailableAmt()).isEqualTo(100000);
        assertThat(bal.getHoldAmt()).isZero();
        assertThat(notificationService.getUnreadCount(buyerSn)).isEqualTo(1);
    }

    @Test
    @DisplayName("반환: 홀딩 없는 참조 건은 거부")
    void releaseWithoutHold() {
        assertThatThrownBy(() -> pointService.releaseHold(buyerSn, RefType.BID, 99L, "잘못된 반환"))
                .isInstanceOf(PointException.class);
    }

    @Test
    @DisplayName("홀딩: 금액이 0 이하이면 거부 (원장에 흔적을 남기지 않음)")
    void holdNonPositiveAmount() {
        assertThatThrownBy(() -> pointService.hold(buyerSn, 0, RefType.BID, 1L, "잘못된 금액"))
                .isInstanceOf(PointException.class);
        assertThatThrownBy(() -> pointService.hold(buyerSn, -10000, RefType.BID, 2L, "잘못된 금액"))
                .isInstanceOf(PointException.class);

        assertThat(pointService.getBalance(buyerSn).getAvailableAmt()).isEqualTo(100000);
    }

    @Test
    @DisplayName("보관금 전환: 홀딩 없는 참조 건은 거부")
    void convertHoldToEscrowWithoutHold() {
        assertThatThrownBy(() -> pointService.convertHoldToEscrow(buyerSn, RefType.BID, 99L, "잘못된 전환"))
                .isInstanceOf(PointException.class);
    }

    @Test
    @DisplayName("관리자 취소 승인 환불: 낙찰 보관금 전액 복원 + 알림 생성 (F-OPS-004)")
    void refundEscrowAfterAdminCancellation() {
        pointService.hold(buyerSn, 30000, RefType.BID, 1L, "입찰 홀딩");
        pointService.convertHoldToEscrow(buyerSn, RefType.BID, 1L, "낙찰 확정");

        long refunded = pointService.refundEscrow(buyerSn, 1L, RefType.BID, 1L, "관리자 취소 승인 환불");

        assertThat(refunded).isEqualTo(30000);
        PointBalance bal = pointService.getBalance(buyerSn);
        assertThat(bal.getAvailableAmt()).isEqualTo(100000);
        assertThat(bal.getTotalAmt()).isEqualTo(100000);
        assertThat(notificationService.getUnreadCount(buyerSn)).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자 취소 승인 환불: 정산 완료된 물건 거래는 차단 (보관금·정산 모두 BID+bidSn 참조로 대사, 옥동민 요청 2026-07-28)")
    void refundEscrowAfterAdminCancellationBlockedWhenSettled() {
        MaterialTradeFixture trade = insertCompletedTrade(30000);
        pointService.hold(buyerSn, 30000, RefType.BID, trade.bidSn(), "입찰 홀딩");
        pointService.convertHoldToEscrow(buyerSn, RefType.BID, trade.bidSn(), "낙찰 확정");

        long stlmSn = settlementService.createPending(trade.tradeSn(), sellerSn, 30000);
        settlementService.complete(stlmSn, TEST_ADMIN_USR_SN);

        assertThatThrownBy(() ->
                pointService.refundEscrow(
                        buyerSn,
                        trade.tradeSn(),
                        RefType.BID,
                        trade.bidSn(),
                        "관리자 취소 승인 환불"))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("정산 지급이 끝나");
    }

    @Test
    @DisplayName("낙찰 전체 흐름: 홀딩 → 보관금 전환 → 정산 대기 → 정산 완료 (ML-PAY-004, F-PAY-042/043)")
    void fullSettlementFlow() {
        MaterialTradeFixture trade = insertCompletedTrade(30000);
        pointService.hold(buyerSn, 30000, RefType.BID, trade.bidSn(), "입찰 홀딩");
        long escrow = pointService.convertHoldToEscrow(
                buyerSn, RefType.BID, trade.bidSn(), "낙찰 확정");
        assertThat(escrow).isEqualTo(30000);

        PointBalance buyerBal = pointService.getBalance(buyerSn);
        assertThat(buyerBal.getHoldAmt()).isZero();
        assertThat(buyerBal.getTotalAmt()).isEqualTo(70000);

        long stlmSn = settlementService.createPending(trade.tradeSn(), sellerSn, escrow);
        settlementService.complete(stlmSn, TEST_ADMIN_USR_SN);

        PointBalance sellerBal = pointService.getBalance(sellerSn);
        assertThat(sellerBal.getSettleableAmt()).isEqualTo(30000);
        assertThat(settlementService.getListByUser(sellerSn))
                .singleElement()
                .satisfies(s -> assertThat(s.getStlmStatusCd()).isEqualTo(SettlementStatus.COMPLETED.getCode()));
        // 정산대기 + 정산완료 알림 2건
        assertThat(notificationService.getUnreadCount(sellerSn)).isEqualTo(2);
    }

    @Test
    @DisplayName("정산 보류/해제: 대기→보류→대기, 보류 중 완료 차단 (F-PAY-044, F-OPS-079)")
    void settlementHoldAndResume() {
        MaterialTradeFixture trade = insertCompletedTrade(30000);
        pointService.hold(buyerSn, 30000, RefType.BID, trade.bidSn(), "입찰 홀딩");
        pointService.convertHoldToEscrow(buyerSn, RefType.BID, trade.bidSn(), "낙찰 확정");
        long stlmSn = settlementService.createPending(trade.tradeSn(), sellerSn, 30000);

        settlementService.holdUp(stlmSn, "거래 분쟁 접수");
        assertThatThrownBy(() -> settlementService.complete(stlmSn, TEST_ADMIN_USR_SN))
                .isInstanceOf(SettlementException.class);

        settlementService.resume(stlmSn);
        settlementService.complete(stlmSn, TEST_ADMIN_USR_SN);
        assertThat(pointService.getBalance(sellerSn).getSettleableAmt()).isEqualTo(30000);
    }

    // ---------- 픽스처 ----------

    private long insertUser(String prefix) {
        String loginId = prefix + "_" + System.nanoTime();
        String email = loginId + "@test.local";
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML_ENC, USR_EML_HMAC, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', ?, ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, prefix, fieldCryptoService.encrypt(email), fieldCryptoService.emailHmac(email));
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /** 낙찰 BID 참조를 포함한 물건거래 완료 픽스처 */
    private MaterialTradeFixture insertCompletedTrade(long amt) {
        jdbc.update("""
                INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_STATUS_CD, PRD_START_AMT, PRD_TRD_METHOD_CD)
                VALUES (?, 2, '테스트 상품', 'PRDC0003', ?,
                        (SELECT C.CMM_CD FROM CMM_CODE C
                         JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'TRDG03' ORDER BY C.CMM_SORT_NO LIMIT 1))
                """, sellerSn, amt);
        long prdSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

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
                VALUES (?, 'AUCC0003', ?, 1000, DATE_SUB(NOW(), INTERVAL 2 HOUR),
                        DATE_SUB(NOW(), INTERVAL 1 HOUR), 0)
                """, prdSn, amt);
        long aucSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("""
                INSERT INTO BID (AUC_SN, USR_SN, BID_AMT, BID_STATUS_CD)
                VALUES (?, ?, ?, 'BIDC0001')
                """, aucSn, buyerSn, amt);
        long bidSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("""
                INSERT INTO TRADE (
                    TRD_TYPE_CD,
                    TRD_STATUS_CD,
                    TRD_AMT,
                    SLLR_USR_SN,
                    BYPR_USR_SN,
                    PRD_SN,
                    BID_SN
                )
                VALUES ('TRDC0001', 'TRDC0006', ?, ?, ?, ?, ?)
                """, amt, sellerSn, buyerSn, prdSn, bidSn);
        long tradeSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return new MaterialTradeFixture(tradeSn, bidSn);
    }

    private record MaterialTradeFixture(long tradeSn, long bidSn) {
    }
}
