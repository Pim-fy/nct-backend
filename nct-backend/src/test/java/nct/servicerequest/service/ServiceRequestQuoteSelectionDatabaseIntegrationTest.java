package nct.servicerequest.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import nct.global.security.crypto.FieldCryptoService;
import nct.point.service.PointService;
import nct.servicerequest.dto.ServiceRequestQuoteSelectionResponse;
import nct.support.ApprovedDatabaseWriteIntegrationTest;
import nct.support.TestGeneratedKeys;

/**
 * 담당자 7 · F-OPS-018/F-SVC-010/F-SVC-013:
 * 견적 선택부터 서비스 거래, 보관금, 채팅방까지 실제 Mapper와 FK로 연결되는지 검증합니다.
 */
@SpringBootTest
@Transactional
@Rollback
class ServiceRequestQuoteSelectionDatabaseIntegrationTest extends ApprovedDatabaseWriteIntegrationTest {

    private static final long QUOTE_AMOUNT = 150_000L;

    @Autowired private ServiceRequestQuoteSelectionService selectionService;
    @Autowired private PointService pointService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FieldCryptoService fieldCryptoService;

    @Test
    @DisplayName("견적 선택은 요청·견적·거래·보관금·채팅을 한 번만 확정한다")
    void selectsQuoteAndCreatesTradeEscrowAndChatExactlyOnce() {
        long requesterSn = insertUser("t_match_requester", "ROLE_USER");
        long providerSn = insertUser("t_match_provider", "ROLE_SERVICE");
        long categorySn = activeServiceCategorySn();
        grantProviderCategory(providerSn, categorySn);
        long requestSn = insertOpenRequest(requesterSn, categorySn);
        long selectedQuoteSn = insertQuote(requestSn, providerSn, QUOTE_AMOUNT);
        long competingQuoteSn = insertQuote(
                requestSn,
                insertUser("t_match_competitor", "ROLE_SERVICE"),
                QUOTE_AMOUNT + 10_000L);
        pointService.creditCharge(requesterSn, QUOTE_AMOUNT * 2, "F-OPS-018 서비스 매칭 테스트 충전");

        ServiceRequestQuoteSelectionResponse first = selectionService.selectQuoteAndCreateTrade(
                requestSn, selectedQuoteSn, requesterSn);
        ServiceRequestQuoteSelectionResponse second = selectionService.selectQuoteAndCreateTrade(
                requestSn, selectedQuoteSn, requesterSn);

        assertThat(second.tradeId()).isEqualTo(first.tradeId());
        assertThat(serviceRequestStatus(requestSn)).isEqualTo("SVCC0003");
        assertThat(quoteStatus(selectedQuoteSn)).isEqualTo("QUTC0004");
        assertThat(quoteStatus(competingQuoteSn)).isEqualTo("QUTC0005");
        assertThat(tradeCountByQuote(selectedQuoteSn)).isEqualTo(1);
        assertThat(tradeStatus(first.tradeId())).isEqualTo("TRDC0003");
        assertThat(tradeAmount(first.tradeId())).isEqualTo(QUOTE_AMOUNT);
        assertThat(activeEscrowAmount(requesterSn, first.tradeId())).isEqualTo(-QUOTE_AMOUNT);
        assertThat(chatRoomCount(first.tradeId())).isEqualTo(1);
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

    private long activeServiceCategorySn() {
        return jdbc.queryForObject("""
                SELECT CAT_SN
                FROM CATEGORY
                WHERE CAT_DOMAIN_CD = 'CATC0002'
                  AND CAT_USE_YN = 'Y'
                ORDER BY CAT_SORT_NO, CAT_SN
                LIMIT 1
                """, Long.class);
    }

    private void grantProviderCategory(long providerSn, long categorySn) {
        jdbc.update("""
                INSERT INTO PROVIDER_CATEGORY_PERMISSION (
                    USR_SN, CAT_SN, PRV_CAT_PRM_STATUS_CD, PRV_CAT_PRM_USE_YN
                ) VALUES (?, ?, 'PRVC0006', 'Y')
                """, providerSn, categorySn);
    }

    private long insertOpenRequest(long requesterSn, long categorySn) {
        return TestGeneratedKeys.insertAndReturnKey(jdbc, """
                INSERT INTO SERVICE_REQUEST (
                    USR_SN, CAT_SN, SVC_REQ_TTL, SVC_REQ_CN,
                    SVC_REQ_BDGT_AMT, SVC_REQ_STATUS_CD
                ) VALUES (?, ?, 'F-OPS-018 매칭 요청', '통합 검증 전용 요청', ?, 'SVCC0002')
                """, requesterSn, categorySn, BigDecimal.valueOf(QUOTE_AMOUNT));
    }

    private long insertQuote(long requestSn, long providerSn, long amount) {
        return TestGeneratedKeys.insertAndReturnKey(jdbc, """
                INSERT INTO QUOTE (
                    SVC_REQ_SN, USR_SN, QUT_AMT, QUT_CN, QUT_STATUS_CD
                ) VALUES (?, ?, ?, 'F-OPS-018 통합 검증 견적', 'QUTC0001')
                """, requestSn, providerSn, BigDecimal.valueOf(amount));
    }

    private String serviceRequestStatus(long requestSn) {
        return jdbc.queryForObject(
                "SELECT SVC_REQ_STATUS_CD FROM SERVICE_REQUEST WHERE SVC_REQ_SN = ?",
                String.class,
                requestSn);
    }

    private String quoteStatus(long quoteSn) {
        return jdbc.queryForObject(
                "SELECT QUT_STATUS_CD FROM QUOTE WHERE QUT_SN = ?",
                String.class,
                quoteSn);
    }

    private int tradeCountByQuote(long quoteSn) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM TRADE WHERE QUT_SN = ?",
                Integer.class,
                quoteSn);
    }

    private String tradeStatus(long tradeSn) {
        return jdbc.queryForObject(
                "SELECT TRD_STATUS_CD FROM TRADE WHERE TRD_SN = ?",
                String.class,
                tradeSn);
    }

    private long tradeAmount(long tradeSn) {
        return jdbc.queryForObject(
                "SELECT TRD_AMT FROM TRADE WHERE TRD_SN = ?",
                Long.class,
                tradeSn);
    }

    private long activeEscrowAmount(long requesterSn, long tradeSn) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(PT_LDG_AMT), 0)
                FROM POINT_LEDGER
                WHERE USR_SN = ?
                  AND PT_LDG_REF_TYPE_CD = 'REFC0005'
                  AND PT_LDG_REF_SN = ?
                  AND PT_LDG_TYPE_CD IN ('PTLC0007', 'PTLC0013')
                """, Long.class, requesterSn, tradeSn);
    }

    private int chatRoomCount(long tradeSn) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM CHAT_ROOM WHERE TRD_SN = ?",
                Integer.class,
                tradeSn);
    }
}
