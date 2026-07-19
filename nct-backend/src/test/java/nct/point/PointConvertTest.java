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

import nct.notification.service.NotificationService;
import nct.point.domain.PointBalance;
import nct.point.exception.PointException;
import nct.point.service.PointService;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [테스트 - 정산가능→사용가능 전환] (F-PAY-010, 조건: 분쟁 없음 확인 후 전환)
 *
 * 공유 DB(NCTDB) 주의사항 (PointFlowTest와 동일):
 * - @Transactional 테스트는 메소드 종료 시 전부 롤백되어 행을 남기지 않는다
 * - 테스트 회원은 매 실행 nanoTime으로 유니크하게 생성되어 팀원 데이터와 충돌하지 않는다
 */
@SpringBootTest
@Transactional
class PointConvertTest {

    @Autowired PointService pointService;
    @Autowired NotificationService notificationService;
    @Autowired JdbcTemplate jdbc;

    long sellerSn;
    long buyerSn;

    @BeforeEach
    void setUpUsers() {
        sellerSn = insertUser("t_cvt_seller");
        buyerSn = insertUser("t_cvt_buyer");
        // 정산가능 잔액 50,000P 직접 적재 (전환 원천)
        jdbc.update("""
                INSERT INTO POINT_LEDGER (USR_SN, PT_LDG_PT_TYPE_CD, PT_LDG_TYPE_CD, PT_LDG_AMT, PT_LDG_BAL_AFTER_AMT, PT_LDG_RSN_CN)
                VALUES (?, 'PTLC0003', 'PTLC0008', 50000, 50000, '테스트 정산 적립')
                """, sellerSn);
    }

    @Test
    @DisplayName("전환: 정산가능 −금액 / 사용가능 +금액, 총 보유 불변 + 알림 1건 (F-PAY-010)")
    void convert() {
        pointService.convertSettleableToAvailable(sellerSn, 30000);

        PointBalance bal = pointService.getBalance(sellerSn);
        assertThat(bal.getSettleableAmt()).isEqualTo(20000);
        assertThat(bal.getAvailableAmt()).isEqualTo(30000);
        assertThat(bal.getTotalAmt()).isEqualTo(50000); // 버킷 이동일 뿐 총 보유는 그대로

        // 원장에 전환(PTLC0012) 유형 두 행이 짝으로 남는다 (합계 0)
        Long sum = jdbc.queryForObject("""
                SELECT SUM(PT_LDG_AMT) FROM POINT_LEDGER
                WHERE USR_SN = ? AND PT_LDG_TYPE_CD = 'PTLC0012'
                """, Long.class, sellerSn);
        assertThat(sum).isZero();
        assertThat(notificationService.getUnreadCount(sellerSn)).isEqualTo(1);
    }

    @Test
    @DisplayName("전환: 정산가능 잔액을 넘는 금액은 거부된다")
    void convertInsufficient() {
        assertThatThrownBy(() -> pointService.convertSettleableToAvailable(sellerSn, 60000))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("부족");

        assertThat(pointService.getBalance(sellerSn).getSettleableAmt()).isEqualTo(50000);
    }

    @Test
    @DisplayName("전환: 진행 중(접수) 거래 문제가 있으면 거부된다 — 분쟁 없음 확인 조건")
    void convertBlockedByActiveDispute() {
        insertDispute("TRDC0016"); // 접수 상태

        assertThatThrownBy(() -> pointService.convertSettleableToAvailable(sellerSn, 10000))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("거래 문제");

        // 잔액·원장 모두 그대로
        PointBalance bal = pointService.getBalance(sellerSn);
        assertThat(bal.getSettleableAmt()).isEqualTo(50000);
        assertThat(bal.getAvailableAmt()).isZero();
    }

    @Test
    @DisplayName("전환: 처리 완료된 거래 문제는 막지 않는다 — 해소된 분쟁은 무관")
    void convertAllowedWhenDisputeResolved() {
        insertDispute("TRDC0018"); // 완료 상태

        pointService.convertSettleableToAvailable(sellerSn, 10000);

        assertThat(pointService.getBalance(sellerSn).getAvailableAmt()).isEqualTo(10000);
    }

    // ---------- 픽스처 ----------

    private long insertUser(String prefix) {
        String loginId = prefix + "_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, prefix, loginId + "@test.local");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /** 판매자(sellerSn)가 당사자인 거래에 지정 상태의 거래 문제를 건다 */
    private void insertDispute(String statusCd) {
        jdbc.update("""
                INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_STATUS_CD, PRD_START_AMT, PRD_TRD_METHOD_CD)
                VALUES (?, 2, '전환 테스트 상품', 'PRDC0003', 10000,
                        (SELECT C.CMM_CD FROM CMM_CODE C
                         JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'TRDG03' ORDER BY C.CMM_SORT_NO LIMIT 1))
                """, sellerSn);
        long prdSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("""
                INSERT INTO TRADE (TRD_TYPE_CD, TRD_STATUS_CD, TRD_AMT, SLLR_USR_SN, BYPR_USR_SN, PRD_SN)
                VALUES ('TRDC0001', 'TRDC0006', 10000, ?, ?, ?)
                """, sellerSn, buyerSn, prdSn);
        long trdSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("""
                INSERT INTO TRADE_DISPUTE (TRD_SN, DSPT_USR_SN, TRD_DSP_TYPE_CD, TRD_DSP_STATUS_CD, TRD_DSP_CN)
                VALUES (?, ?, 'TRDC0014', ?, '전환 차단 테스트용 거래 문제')
                """, trdSn, buyerSn, statusCd);
    }
}
