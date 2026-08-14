package nct.ops.operation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
import nct.ops.operation.domain.ReportEnforcementAction;
import nct.ops.operation.port.AdminReportDecision;
import nct.point.service.PointService;
import nct.settlement.service.SettlementService;
import nct.support.ApprovedDatabaseWriteIntegrationTest;
import nct.support.TestGeneratedKeys;
import nct.trade.dto.ServiceTradeDisputeRequest;
import nct.trade.service.TradeService;

/**
 * 승인된 공용 DB에서 서비스 거래 신고 판정의 실제 Mapper·FK·원장·정산 전이를 확인한다.
 * 테스트 데이터는 Spring 테스트 트랜잭션으로 항상 롤백해 공유 DB에 남기지 않는다.
 */
@SpringBootTest
@Transactional
@Rollback
class AdminDisputeDecisionDatabaseIntegrationTest extends ApprovedDatabaseWriteIntegrationTest {

    private static final long TRADE_AMOUNT = 5_000L;

    @Autowired private TradeService tradeService;
    @Autowired private AdminReportOperationService reportOperationService;
    @Autowired private PointService pointService;
    @Autowired private SettlementService settlementService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FieldCryptoService fieldCryptoService;

    @Test
    @DisplayName("서비스 거래 신고 전액 환불은 거래·신고·정산·보관금을 함께 변경하고 같은 판정은 중복 처리하지 않는다")
    void refundsServiceTradeReportAndPreventsDuplicateDecision() {
        ReportFixture fixture = createOpenServiceReport();

        assertThat(tradeStatus(fixture.tradeSn())).isEqualTo("TRDC0007");
        assertThat(reportStatus(fixture.reportSn())).isEqualTo("ABSC0001");
        assertThat(settlementStatus(fixture.tradeSn())).isEqualTo("STLC0002");

        reportOperationService.decide(
                fixture.reportSn(), AdminReportDecision.PROCESSED, AdminDisputeDecision.REFUND,
                ReportEnforcementAction.NONE, "전액 환불 확정", fixture.adminSn());

        assertThat(tradeStatus(fixture.tradeSn())).isEqualTo("TRDC0008");
        assertThat(reportStatus(fixture.reportSn())).isEqualTo("ABSC0003");
        assertThat(reportResult(fixture.reportSn())).isEqualTo("TRDC0012");
        assertThat(settlementStatus(fixture.tradeSn())).isEqualTo("STLC0004");
        assertThat(activeEscrowAmount(fixture.requesterSn(), fixture.tradeSn())).isZero();

        reportOperationService.decide(
                fixture.reportSn(), AdminReportDecision.PROCESSED, AdminDisputeDecision.REFUND,
                ReportEnforcementAction.NONE, "전액 환불 확정", fixture.adminSn());

        assertThat(refundLedgerCount(fixture.requesterSn(), fixture.tradeSn())).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자 정산 보류 판정은 거래·정산 보류를 유지하고 같은 판정은 중복 처리하지 않는다")
    void holdsServiceTradeReportAndPreventsDuplicateDecision() {
        ReportFixture fixture = createOpenServiceReport();
        long auditCountBeforeDecision = reportAuditCount(fixture.reportSn());

        reportOperationService.decide(
                fixture.reportSn(), AdminReportDecision.PROCESSING, AdminDisputeDecision.HOLD,
                ReportEnforcementAction.NONE, "추가 확인이 필요합니다", fixture.adminSn());

        assertThat(tradeStatus(fixture.tradeSn())).isEqualTo("TRDC0007");
        assertThat(reportStatus(fixture.reportSn())).isEqualTo("ABSC0002");
        assertThat(reportResult(fixture.reportSn())).isEqualTo("TRDC0013");
        assertThat(settlementStatus(fixture.tradeSn())).isEqualTo("STLC0002");
        long auditCountAfterFirstDecision = reportAuditCount(fixture.reportSn());
        assertThat(auditCountAfterFirstDecision).isGreaterThan(auditCountBeforeDecision);

        reportOperationService.decide(
                fixture.reportSn(), AdminReportDecision.PROCESSING, AdminDisputeDecision.HOLD,
                ReportEnforcementAction.NONE, "추가 확인이 필요합니다", fixture.adminSn());

        assertThat(reportAuditCount(fixture.reportSn())).isEqualTo(auditCountAfterFirstDecision);
    }

    @Test
    @DisplayName("관리자 처리 완료와 반려는 접수 직전 거래 상태와 정산 대기를 각각 복구한다")
    void completesAndRejectsServiceTradeReports() {
        ReportFixture completeFixture = createOpenServiceReport();

        reportOperationService.decide(
                completeFixture.reportSn(), AdminReportDecision.PROCESSED,
                AdminDisputeDecision.COMPLETE, ReportEnforcementAction.NONE,
                "당사자 조정이 완료됐습니다", completeFixture.adminSn());

        assertThat(tradeStatus(completeFixture.tradeSn())).isEqualTo("TRDC0003");
        assertThat(reportStatus(completeFixture.reportSn())).isEqualTo("ABSC0003");
        assertThat(reportResult(completeFixture.reportSn())).isEqualTo("TRDC0011");
        assertThat(settlementStatus(completeFixture.tradeSn())).isEqualTo("STLC0001");

        ReportFixture rejectFixture = createOpenServiceReport();

        reportOperationService.decide(
                rejectFixture.reportSn(), AdminReportDecision.REJECTED,
                AdminDisputeDecision.REJECT, ReportEnforcementAction.NONE,
                "접수 요건에 맞지 않습니다", rejectFixture.adminSn());

        assertThat(tradeStatus(rejectFixture.tradeSn())).isEqualTo("TRDC0003");
        assertThat(reportStatus(rejectFixture.reportSn())).isEqualTo("ABSC0004");
        assertThat(reportResult(rejectFixture.reportSn())).isNull();
        assertThat(settlementStatus(rejectFixture.tradeSn())).isEqualTo("STLC0001");
    }

    @Test
    @DisplayName("진행 중 거래 신고가 실제 연결돼 있으면 서비스 자동완료를 차단한다")
    void openTradeReportBlocksServiceAutoCompletionAgainstActualMapper() {
        ReportFixture fixture = createOpenServiceReport();
        jdbc.update("""
                UPDATE TRADE
                SET TRD_STATUS_CD = 'TRDC0005',
                    TRD_AUTO_CMPL_DT = DATE_SUB(NOW(), INTERVAL 1 MINUTE)
                WHERE TRD_SN = ?
                """, fixture.tradeSn());

        assertThatThrownBy(() -> tradeService.completeExpiredServiceConfirmation(
                fixture.tradeSn(), LocalDateTime.now()))
                .hasMessageContaining("처리 중인 거래 문제");

        assertThat(tradeStatus(fixture.tradeSn())).isEqualTo("TRDC0005");
        assertThat(reportStatus(fixture.reportSn())).isEqualTo("ABSC0001");
        assertThat(settlementStatus(fixture.tradeSn())).isEqualTo("STLC0002");
    }

    private ReportFixture createOpenServiceReport() {
        long requesterSn = insertUser("t_dispute_requester");
        long providerSn = insertUser("t_dispute_provider");
        long adminSn = insertUser("t_dispute_admin", "ROLE_ADMIN");
        long tradeSn = insertServiceTrade(requesterSn, providerSn);

        pointService.creditCharge(requesterSn, TRADE_AMOUNT * 2, "거래 신고 E2E 테스트 충전");
        pointService.debitEscrow(
                requesterSn, TRADE_AMOUNT, RefType.TRADE, tradeSn, "거래 신고 E2E 테스트 보관금");
        settlementService.createPending(tradeSn, providerSn, TRADE_AMOUNT);

        ServiceTradeDisputeRequest request = new ServiceTradeDisputeRequest();
        request.setReportTypeCode("ABRC0008");
        request.setContent("거래 신고 E2E 판정 검증");
        tradeService.registerTradeReport(tradeSn, requesterSn, request);

        long reportSn = jdbc.queryForObject("""
                SELECT ABR_SN
                FROM ABUSE_REPORT_TRADE
                WHERE TRD_SN = ?
                ORDER BY ABR_SN DESC
                LIMIT 1
                """, Long.class, tradeSn);
        return new ReportFixture(requesterSn, adminSn, tradeSn, reportSn);
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
                ) VALUES (?, ?, '거래 신고 E2E 서비스 요청', '테스트 전용 요청', ?, ?)
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
        return insertUser(prefix, "ROLE_USER");
    }

    private long insertUser(String prefix, String roleCode) {
        String loginId = prefix + '_' + System.nanoTime();
        String email = loginId + "@test.local";
        return TestGeneratedKeys.insertAndReturnKey(jdbc, """
                INSERT INTO USERS (
                    USR_LOGIN_ID, USR_PSWD_HASH, USR_NM,
                    USR_EML_ENC, USR_EML_HMAC, USR_STATUS_CD, USR_ROLE_CD
                ) VALUES (?, '{noop}test', ?, ?, ?, 'USRC0001', ?)
                """, loginId, loginId, fieldCryptoService.encrypt(email),
                fieldCryptoService.emailHmac(email), roleCode);
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

    private String reportStatus(long reportSn) {
        return jdbc.queryForObject(
                "SELECT ABR_STATUS_CD FROM ABUSE_REPORT WHERE ABR_SN = ?", String.class, reportSn);
    }

    private String reportResult(long reportSn) {
        return jdbc.queryForObject(
                "SELECT ABR_TRD_RSLT_CD FROM ABUSE_REPORT_TRADE WHERE ABR_SN = ?", String.class, reportSn);
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

    private long reportAuditCount(long reportSn) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM AUDIT_LOG
                WHERE AUD_LOG_REF_TYPE_CD = 'REFC0018'
                  AND AUD_LOG_REF_SN = ?
                """, Long.class, reportSn);
        return count == null ? 0L : count;
    }

    private record ReportFixture(long requesterSn, long adminSn, long tradeSn, long reportSn) {
    }
}
