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
import nct.point.domain.PointBalance;
import nct.point.exception.InsufficientPointException;
import nct.point.exception.PointException;
import nct.point.service.PointService;

/**
 * Claude Code 작성 (BJN, 2026-07-20)
 *
 * [테스트 - 보관금(에스크로) 계약] (F-SVC-013 보관금 생성 / F-SVC-015 정산가능 전환 / 분쟁 판정 환불)
 *
 * 담당자4·5가 2단계에서 호출할 계약을 선구현·선검증한다 (팀전달_보관금_환불계약_260720.md 참조).
 *
 * 공유 DB(NCTDB) 주의사항 (PointFlowTest와 동일):
 * - @Transactional 테스트는 메소드 종료 시 전부 롤백되어 행을 남기지 않는다
 * - 테스트 회원은 매 실행 nanoTime으로 유니크하게 생성되어 팀원 데이터와 충돌하지 않는다
 * - ⚠️ POINT_LEDGER.PT_LDG_TYPE_CD에 CMM_CODE FK가 걸려 있어, 실DB에 PTLC0013(환불) 코드가
 *   등록되기 전에는 환불 관련 테스트가 FK 위반으로 실패한다 (팀전달_환불코드_정본요청_260720.md의
 *   INSERT를 먼저 적용할 것 — PTLC0012 전환 코드 때와 같은 절차)
 */
@SpringBootTest
@Transactional
class PointEscrowTest {

    @Autowired PointService pointService;
    @Autowired NotificationService notificationService;
    @Autowired JdbcTemplate jdbc;

    long requesterSn; // 서비스 의뢰자 (보관금을 내는 쪽)
    long providerSn;  // 서비스 제공자 (정산대금을 받는 쪽)
    long trdSn;       // 서비스 거래 (TRDC0002)

    @BeforeEach
    void setUp() {
        requesterSn = insertUser("t_esc_req");
        providerSn = insertUser("t_esc_prv");
        // 의뢰자 사용가능 잔액 50,000P 직접 적재 (보관금 재원)
        jdbc.update("""
                INSERT INTO POINT_LEDGER (USR_SN, PT_LDG_PT_TYPE_CD, PT_LDG_TYPE_CD, PT_LDG_AMT, PT_LDG_BAL_AFTER_AMT, PT_LDG_RSN_CN)
                VALUES (?, 'PTLC0001', 'PTLC0004', 50000, 50000, '테스트 충전')
                """, requesterSn);
        trdSn = insertServiceTrade();
    }

    // ---------- F-SVC-013 보관금 생성 ----------

    @Test
    @DisplayName("보관금 생성: 사용가능 −금액, 총 보유 감소(회원 버킷 밖으로 분리), 참조 기록 (F-SVC-013)")
    void debitEscrow() {
        pointService.debitEscrow(requesterSn, 30000, RefType.TRADE, trdSn, "견적 선택 보관금");

        PointBalance bal = pointService.getBalance(requesterSn);
        assertThat(bal.getAvailableAmt()).isEqualTo(20000);
        assertThat(bal.getTotalAmt()).isEqualTo(20000); // 보관금은 회원 잔액 밖 (경매 보관금전환과 동일 의미)

        // 원장에 보관금전환(PTLC0007) −행이 참조와 함께 남는다
        Long amt = jdbc.queryForObject("""
                SELECT SUM(PT_LDG_AMT) FROM POINT_LEDGER
                WHERE USR_SN = ? AND PT_LDG_TYPE_CD = 'PTLC0007'
                  AND PT_LDG_REF_TYPE_CD = ? AND PT_LDG_REF_SN = ?
                """, Long.class, requesterSn, RefType.TRADE.getCode(), trdSn);
        assertThat(amt).isEqualTo(-30000);
    }

    @Test
    @DisplayName("보관금 생성: 사용가능 잔액을 넘는 금액은 거부되고 원장이 남지 않는다")
    void debitEscrowInsufficient() {
        assertThatThrownBy(() -> pointService.debitEscrow(requesterSn, 60000, RefType.TRADE, trdSn, "초과 보관금"))
                .isInstanceOf(InsufficientPointException.class);

        assertThat(pointService.getBalance(requesterSn).getAvailableAmt()).isEqualTo(50000);
    }

    @Test
    @DisplayName("보관금 생성: 같은 거래에 두 번 결제하면 중복으로 거부된다 — 이중 결제 방지")
    void debitEscrowDuplicate() {
        pointService.debitEscrow(requesterSn, 30000, RefType.TRADE, trdSn, "견적 선택 보관금");

        assertThatThrownBy(() -> pointService.debitEscrow(requesterSn, 10000, RefType.TRADE, trdSn, "중복 보관금"))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("이미 보관금");

        assertThat(pointService.getBalance(requesterSn).getAvailableAmt()).isEqualTo(20000); // 첫 결제만 반영
    }

    // ---------- F-SVC-015 정산가능 전환 ----------

    @Test
    @DisplayName("정산 전환: 제공자 정산가능 +보관금액, 사유에 (수수료 0원), 제공자 알림 1건 (F-SVC-015)")
    void creditEscrowToSettleable() {
        pointService.debitEscrow(requesterSn, 30000, RefType.TRADE, trdSn, "견적 선택 보관금");

        long credited = pointService.creditEscrowToSettleable(providerSn, RefType.TRADE, trdSn, "서비스 완료 정산");

        assertThat(credited).isEqualTo(30000); // 금액은 호출자가 아니라 원장(보관금 잔존액)이 결정
        assertThat(pointService.getBalance(providerSn).getSettleableAmt()).isEqualTo(30000);

        // 수수료 0원 명시(F-PAY-008/009)와 알림(제공자 구분)까지 같은 트랜잭션에서 기록됐는지
        String reason = jdbc.queryForObject("""
                SELECT PT_LDG_RSN_CN FROM POINT_LEDGER
                WHERE USR_SN = ? AND PT_LDG_TYPE_CD = 'PTLC0008'
                """, String.class, providerSn);
        assertThat(reason).contains("(수수료 0원)");
        assertThat(notificationService.getUnreadCount(providerSn)).isEqualTo(1);
    }

    @Test
    @DisplayName("정산 전환: 진행 중(접수) 거래 문제가 있으면 차단된다 — 분쟁 접수 시 정산 보류")
    void creditEscrowBlockedByDispute() {
        pointService.debitEscrow(requesterSn, 30000, RefType.TRADE, trdSn, "견적 선택 보관금");
        insertDispute(trdSn, "TRDC0016"); // 접수 상태

        assertThatThrownBy(() -> pointService.creditEscrowToSettleable(providerSn, RefType.TRADE, trdSn, "서비스 완료 정산"))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("거래 문제");
        assertThat(pointService.getBalance(providerSn).getSettleableAmt()).isZero();
    }

    @Test
    @DisplayName("정산 전환: 처리 완료된 거래 문제는 막지 않는다 — 해소된 분쟁은 무관")
    void creditEscrowAllowedWhenDisputeResolved() {
        pointService.debitEscrow(requesterSn, 30000, RefType.TRADE, trdSn, "견적 선택 보관금");
        insertDispute(trdSn, "TRDC0018"); // 완료 상태

        pointService.creditEscrowToSettleable(providerSn, RefType.TRADE, trdSn, "서비스 완료 정산");

        assertThat(pointService.getBalance(providerSn).getSettleableAmt()).isEqualTo(30000);
    }

    @Test
    @DisplayName("정산 전환: 같은 거래로 두 번 전환하면 이중 정산으로 거부된다")
    void creditEscrowTwiceBlocked() {
        pointService.debitEscrow(requesterSn, 30000, RefType.TRADE, trdSn, "견적 선택 보관금");
        pointService.creditEscrowToSettleable(providerSn, RefType.TRADE, trdSn, "서비스 완료 정산");

        assertThatThrownBy(() -> pointService.creditEscrowToSettleable(providerSn, RefType.TRADE, trdSn, "중복 정산"))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("이미 정산");

        assertThat(pointService.getBalance(providerSn).getSettleableAmt()).isEqualTo(30000); // 1회분만
    }

    @Test
    @DisplayName("정산 전환: 보관금이 없는 거래는 거부된다")
    void creditEscrowWithoutEscrow() {
        assertThatThrownBy(() -> pointService.creditEscrowToSettleable(providerSn, RefType.TRADE, trdSn, "보관금 없는 정산"))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("보관금이 없습니다");
    }

    // ---------- 분쟁 판정 환불 ----------

    @Test
    @DisplayName("환불: 판정 '환불' 시 의뢰자 사용가능 복원(전액) + 지갑 알림 — 물건·서비스 공통 계약")
    void refundEscrow() {
        pointService.debitEscrow(requesterSn, 30000, RefType.TRADE, trdSn, "견적 선택 보관금");

        long refunded = pointService.refundEscrow(requesterSn, trdSn, RefType.TRADE, trdSn, "거래 문제 판정 환불");

        assertThat(refunded).isEqualTo(30000);
        PointBalance bal = pointService.getBalance(requesterSn);
        assertThat(bal.getAvailableAmt()).isEqualTo(50000); // 원상복구
        assertThat(bal.getTotalAmt()).isEqualTo(50000);
        assertThat(notificationService.getList(requesterSn))
                .anySatisfy(n -> assertThat(n.getNtfTtl()).isEqualTo("포인트 환불"));
    }

    @Test
    @DisplayName("환불: 같은 건을 두 번 환불하면 거부된다 — 보관금·환불 합산 0이면 소멸로 판정")
    void refundEscrowTwiceBlocked() {
        pointService.debitEscrow(requesterSn, 30000, RefType.TRADE, trdSn, "견적 선택 보관금");
        pointService.refundEscrow(requesterSn, trdSn, RefType.TRADE, trdSn, "거래 문제 판정 환불");

        assertThatThrownBy(() -> pointService.refundEscrow(requesterSn, trdSn, RefType.TRADE, trdSn, "중복 환불"))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("보관금이 없습니다");

        assertThat(pointService.getBalance(requesterSn).getAvailableAmt()).isEqualTo(50000); // 1회분만
    }

    @Test
    @DisplayName("순서 역전 방지: 정산 지급 후에는 환불 불가, 환불 후에는 정산 전환 불가")
    void refundAndSettleAreMutuallyExclusive() {
        // (1) 정산이 먼저 끝난 건은 환불할 수 없다 — 이미 제공자에게 지급된 돈 (관리자 수동 보정 영역)
        pointService.debitEscrow(requesterSn, 30000, RefType.TRADE, trdSn, "견적 선택 보관금");
        pointService.creditEscrowToSettleable(providerSn, RefType.TRADE, trdSn, "서비스 완료 정산");
        assertThatThrownBy(() -> pointService.refundEscrow(requesterSn, trdSn, RefType.TRADE, trdSn, "정산 후 환불 시도"))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("정산 지급이 끝나");

        // (2) 환불이 먼저 끝난 건은 정산 전환할 수 없다 — 보관금이 이미 소멸
        long trdSn2 = insertServiceTrade();
        pointService.debitEscrow(requesterSn, 10000, RefType.TRADE, trdSn2, "견적 선택 보관금");
        pointService.refundEscrow(requesterSn, trdSn2, RefType.TRADE, trdSn2, "거래 문제 판정 환불");
        assertThatThrownBy(() -> pointService.creditEscrowToSettleable(providerSn, RefType.TRADE, trdSn2, "환불 후 정산 시도"))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("보관금이 없습니다");
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

    /**
     * 서비스 거래(TRDC0002) 생성 — 정본 CHECK(CHK_TRADE_REF_COMBINATION)가 서비스 거래에
     * 요청·견적 참조를 강제하므로 SERVICE_REQUEST → QUOTE → TRADE 순서로 만든다.
     * 상태코드는 하드코딩 대신 그룹(SVCG01/QUTG01)의 첫 자식을 쓴다 (PointConvertTest의 TRDG03 방식)
     */
    private long insertServiceTrade() {
        jdbc.update("""
                INSERT INTO SERVICE_REQUEST (USR_SN, CAT_SN, SVC_REQ_TTL, SVC_REQ_STATUS_CD)
                VALUES (?, 2, '보관금 테스트 요청',
                        (SELECT C.CMM_CD FROM CMM_CODE C
                         JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'SVCG01' ORDER BY C.CMM_SORT_NO LIMIT 1))
                """, requesterSn);
        long svcReqSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("""
                INSERT INTO QUOTE (SVC_REQ_SN, USR_SN, QUT_AMT, QUT_STATUS_CD)
                VALUES (?, ?, 30000,
                        (SELECT C.CMM_CD FROM CMM_CODE C
                         JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'QUTG01' ORDER BY C.CMM_SORT_NO LIMIT 1))
                """, svcReqSn, providerSn);
        long qutSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("""
                INSERT INTO TRADE (TRD_TYPE_CD, TRD_STATUS_CD, TRD_AMT, REQ_USR_SN, PRV_USR_SN, SVC_REQ_SN, QUT_SN)
                VALUES ('TRDC0002', 'TRDC0003', 30000, ?, ?, ?, ?)
                """, requesterSn, providerSn, svcReqSn, qutSn);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /** 해당 거래에 지정 상태의 거래 문제를 건다 (PointConvertTest와 같은 방식, 서비스 거래 버전) */
    private void insertDispute(long targetTrdSn, String statusCd) {
        jdbc.update("""
                INSERT INTO TRADE_DISPUTE (TRD_SN, DSPT_USR_SN, TRD_DSP_TYPE_CD, TRD_DSP_STATUS_CD, TRD_DSP_CN)
                VALUES (?, ?, 'TRDC0013', ?, '정산 전환 차단 테스트용 거래 문제')
                """, targetTrdSn, requesterSn, statusCd);
    }
}
