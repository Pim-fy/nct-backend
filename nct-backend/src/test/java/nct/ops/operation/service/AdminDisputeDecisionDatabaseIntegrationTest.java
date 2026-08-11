package nct.ops.operation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import nct.common.domain.RefType;
import nct.global.security.crypto.FieldCryptoService;
import nct.ops.operation.domain.AdminDisputeDecision;
import nct.point.service.PointService;
import nct.settlement.service.SettlementService;
import nct.support.ApprovedDatabaseWriteIntegrationTest;
import nct.support.TestGeneratedKeys;
import nct.trade.dto.ServiceTradeDisputeRequest;
import nct.trade.service.TradeService;

/**
 * 승인된 공용 DB에서 서비스 거래 분쟁 환불 판정의 실제 Mapper·FK·원장·정산 전이를 확인한다.
 * 테스트 데이터는 Spring 테스트 트랜잭션으로 항상 롤백해 공유 DB에 남기지 않는다.
 */
@SpringBootTest
@Transactional
@Rollback
class AdminDisputeDecisionDatabaseIntegrationTest extends ApprovedDatabaseWriteIntegrationTest {

    private static final long TRADE_AMOUNT = 5_000L;

    @Autowired private TradeService tradeService;
    @Autowired private AdminDisputeDecisionService decisionService;
    @Autowired private PointService pointService;
    @Autowired private SettlementService settlementService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FieldCryptoService fieldCryptoService;

    @Test
    @DisplayName("서비스 거래 문제 전액 환불은 거래·분쟁·정산·보관금을 함께 변경하고 같은 판정은 중복 처리하지 않는다")
    void refundsServiceTradeDisputeAndPreventsDuplicateDecision() {
        DisputeFixture fixture = createOpenServiceDispute();

        assertThat(tradeStatus(fixture.tradeSn())).isEqualTo("TRDC0007");
        assertThat(disputeStatus(fixture.disputeSn())).isEqualTo("TRDC0016");
        assertThat(settlementStatus(fixture.tradeSn())).isEqualTo("STLC0002");

        var first = decisionService.decide(
                fixture.disputeSn(), AdminDisputeDecision.REFUND, "전액 환불 확정", fixture.adminSn());

        assertThat(first.changed()).isTrue();
        assertThat(first.refundedAmount()).isEqualTo(TRADE_AMOUNT);
        assertThat(tradeStatus(fixture.tradeSn())).isEqualTo("TRDC0008");
        assertThat(disputeStatus(fixture.disputeSn())).isEqualTo("TRDC0018");
        assertThat(disputeResult(fixture.disputeSn())).isEqualTo("TRDC0022");
        assertThat(settlementStatus(fixture.tradeSn())).isEqualTo("STLC0004");
        assertThat(activeEscrowAmount(fixture.requesterSn(), fixture.tradeSn())).isZero();

        var repeated = decisionService.decide(
                fixture.disputeSn(), AdminDisputeDecision.REFUND, "전액 환불 확정", fixture.adminSn());

        assertThat(repeated.changed()).isFalse();
        assertThat(repeated.refundedAmount()).isZero();
        assertThat(refundLedgerCount(fixture.requesterSn(), fixture.tradeSn())).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자 정산 보류 판정은 거래·정산 보류를 유지하고 같은 판정은 중복 처리하지 않는다")
    void holdsServiceTradeDisputeAndPreventsDuplicateDecision() {
        DisputeFixture fixture = createOpenServiceDispute();

        var first = decisionService.decide(
                fixture.disputeSn(), AdminDisputeDecision.HOLD, "추가 확인이 필요합니다", fixture.adminSn());

        assertThat(first.changed()).isTrue();
        assertThat(first.refundedAmount()).isZero();
        assertThat(tradeStatus(fixture.tradeSn())).isEqualTo("TRDC0007");
        assertThat(disputeStatus(fixture.disputeSn())).isEqualTo("TRDC0017");
        assertThat(disputeResult(fixture.disputeSn())).isEqualTo("TRDC0023");
        assertThat(settlementStatus(fixture.tradeSn())).isEqualTo("STLC0002");

        var repeated = decisionService.decide(
                fixture.disputeSn(), AdminDisputeDecision.HOLD, "추가 확인이 필요합니다", fixture.adminSn());

        assertThat(repeated.changed()).isFalse();
    }

    @Test
    @DisplayName("관리자 처리 완료와 반려는 접수 직전 거래 상태와 정산 대기를 각각 복구한다")
    void completesAndRejectsServiceTradeDisputes() {
        DisputeFixture completeFixture = createOpenServiceDispute();

        decisionService.decide(
                completeFixture.disputeSn(), AdminDisputeDecision.COMPLETE,
                "당사자 조정이 완료됐습니다", completeFixture.adminSn());

        assertThat(tradeStatus(completeFixture.tradeSn())).isEqualTo("TRDC0003");
        assertThat(disputeStatus(completeFixture.disputeSn())).isEqualTo("TRDC0018");
        assertThat(disputeResult(completeFixture.disputeSn())).isEqualTo("TRDC0021");
        assertThat(settlementStatus(completeFixture.tradeSn())).isEqualTo("STLC0001");

        DisputeFixture rejectFixture = createOpenServiceDispute();

        decisionService.decide(
                rejectFixture.disputeSn(), AdminDisputeDecision.REJECT,
                "접수 요건에 맞지 않습니다", rejectFixture.adminSn());

        assertThat(tradeStatus(rejectFixture.tradeSn())).isEqualTo("TRDC0003");
        assertThat(disputeStatus(rejectFixture.disputeSn())).isEqualTo("TRDC0019");
        assertThat(disputeResult(rejectFixture.disputeSn())).isNull();
        assertThat(settlementStatus(rejectFixture.tradeSn())).isEqualTo("STLC0001");
    }

    private DisputeFixture createOpenServiceDispute() {
        long requesterSn = insertUser("t_dispute_requester");
        long providerSn = insertUser("t_dispute_provider");
        long adminSn = insertUser("t_dispute_admin");
        long tradeSn = insertServiceTrade(requesterSn, providerSn);

        pointService.creditCharge(requesterSn, TRADE_AMOUNT * 2, "분쟁 E2E 테스트 충전");
        pointService.debitEscrow(
                requesterSn, TRADE_AMOUNT, RefType.TRADE, tradeSn, "분쟁 E2E 테스트 보관금");
        settlementService.createPending(tradeSn, providerSn, TRADE_AMOUNT);

        ServiceTradeDisputeRequest request = new ServiceTradeDisputeRequest();
        request.setDisputeTypeCode("TRDC0011");
        request.setContent("분쟁 E2E 판정 검증");
        tradeService.registerServiceTradeDispute(tradeSn, requesterSn, request);

        long disputeSn = jdbc.queryForObject("""
                SELECT TRD_DSP_SN
                FROM TRADE_DISPUTE
                WHERE TRD_SN = ?
                ORDER BY TRD_DSP_SN DESC
                LIMIT 1
                """, Long.class, tradeSn);
        return new DisputeFixture(requesterSn, adminSn, tradeSn, disputeSn);
    }

    private long insertServiceTrade(long requesterSn, long providerSn) {
        long categorySn = jdbc.queryForObject("""
                SELECT CAT_SN
                FROM CATEGORY
                WHERE CAT_USE_YN = 'Y'
                ORDER BY CAT_SN
                LIMIT 1
                """, Long.class);
        long requestSn = TestGeneratedKeys.insertAndReturnKey(jdbc, """
                INSERT INTO SERVICE_REQUEST (
                    USR_SN, CAT_SN, SVC_REQ_TTL, SVC_REQ_CN, SVC_REQ_BDGT_AMT, SVC_REQ_STATUS_CD
                ) VALUES (?, ?, '분쟁 E2E 서비스 요청', '테스트 전용 요청', ?, ?)
                """, requesterSn, categorySn, BigDecimal.valueOf(TRADE_AMOUNT), activeChildCode("SVCG01"));
        long quoteSn = TestGeneratedKeys.insertAndReturnKey(jdbc, """
                INSERT INTO QUOTE (SVC_REQ_SN, USR_SN, QUT_AMT, QUT_CN, QUT_STATUS_CD)
                VALUES (?, ?, ?, '테스트 전용 견적', ?)
                """, requestSn, providerSn, BigDecimal.valueOf(TRADE_AMOUNT), activeChildCode("QUTG01"));
        return TestGeneratedKeys.insertAndReturnKey(jdbc, """
                INSERT INTO TRADE (
                    REQ_USR_SN, PRV_USR_SN, SVC_REQ_SN, QUT_SN,
                    TRD_TYPE_CD, TRD_STATUS_CD, TRD_AMT
                ) VALUES (?, ?, ?, ?, 'TRDC0002', 'TRDC0003', ?)
                """, requesterSn, providerSn, requestSn, quoteSn, BigDecimal.valueOf(TRADE_AMOUNT));
    }

    private long insertUser(String prefix) {
        String loginId = prefix + '_' + System.nanoTime();
        String email = loginId + "@test.local";
        return TestGeneratedKeys.insertAndReturnKey(jdbc, """
                INSERT INTO USERS (
                    USR_LOGIN_ID, USR_PSWD_HASH, USR_NM,
                    USR_EML_ENC, USR_EML_HMAC, USR_STATUS_CD, USR_ROLE_CD
                ) VALUES (?, '{noop}test', ?, ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, loginId, fieldCryptoService.encrypt(email), fieldCryptoService.emailHmac(email));
    }

    private String activeChildCode(String parentCode) {
        return jdbc.queryForObject("""
                SELECT child.CMM_CD
                FROM CMM_CODE child
                JOIN CMM_CODE parent ON parent.CMM_SN = child.CMM_PARENT_SN
                WHERE parent.CMM_CD = ?
                  AND child.CMM_USE_YN = 'Y'
                ORDER BY child.CMM_SORT_NO, child.CMM_SN
                LIMIT 1
                """, String.class, parentCode);
    }

    private String tradeStatus(long tradeSn) {
        return jdbc.queryForObject(
                "SELECT TRD_STATUS_CD FROM TRADE WHERE TRD_SN = ?", String.class, tradeSn);
    }

    private String disputeStatus(long disputeSn) {
        return jdbc.queryForObject(
                "SELECT TRD_DSP_STATUS_CD FROM TRADE_DISPUTE WHERE TRD_DSP_SN = ?", String.class, disputeSn);
    }

    private String disputeResult(long disputeSn) {
        return jdbc.queryForObject(
                "SELECT TRD_DSP_RSLT_CD FROM TRADE_DISPUTE WHERE TRD_DSP_SN = ?", String.class, disputeSn);
    }

    private String settlementStatus(long tradeSn) {
        return jdbc.queryForObject(
                "SELECT STLM_STATUS_CD FROM SETTLEMENT WHERE TRD_SN = ?", String.class, tradeSn);
    }

    private long activeEscrowAmount(long requesterSn, long tradeSn) {
        Long amount = jdbc.queryForObject("""
                SELECT COALESCE(SUM(PT_LDG_AMT), 0)
                FROM POINT_LEDGER
                WHERE USR_SN = ?
                  AND PT_LDG_REF_TYPE_CD = 'REFC0005'
                  AND PT_LDG_REF_SN = ?
                  AND PT_LDG_TYPE_CD IN ('PTLC0007', 'PTLC0013')
                """, Long.class, requesterSn, tradeSn);
        return amount == null ? 0L : amount;
    }

    private long refundLedgerCount(long requesterSn, long tradeSn) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM POINT_LEDGER
                WHERE USR_SN = ?
                  AND PT_LDG_REF_TYPE_CD = 'REFC0005'
                  AND PT_LDG_REF_SN = ?
                  AND PT_LDG_TYPE_CD = 'PTLC0013'
                """, Long.class, requesterSn, tradeSn);
        return count == null ? 0L : count;
    }

    private record DisputeFixture(long requesterSn, long adminSn, long tradeSn, long disputeSn) {
    }
}
