package nct.trade;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import nct.common.domain.RefType;
import nct.global.security.crypto.FieldCryptoService;
import nct.notification.service.NotificationService;
import nct.point.service.PointService;
import nct.setting.mapper.SystemSettingAdminMapper;
import nct.trade.mapper.TradeMapper;
import nct.trade.scheduler.TradeAutoCompletionScheduler;
import nct.trade.service.TradeService;

/**
 * 만료 거래의 스케줄러 경로를 실제 DB에서 검증한다.
 *
 * <p>테스트 전체를 트랜잭션으로 감싸므로 생성한 회원·경매·거래·정산·알림은 종료 시 롤백된다.</p>
 */
@SpringBootTest
@Transactional
class TradeAutoCompletionFlowTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FieldCryptoService fieldCryptoService;

    @Autowired
    private SystemSettingAdminMapper systemSettingMapper;

    @Autowired
    private TradeMapper tradeMapper;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PointService pointService;

    @Test
    void schedulerCompletesExpiredTradeAndCreatesSinglePendingSettlement() {
        long sellerUserId = insertUser("auto_seller");
        long buyerUserId = insertUser("auto_buyer");
        insertAvailablePoint(buyerUserId, 10_000L);
        MaterialTradeFixture trade = insertExpiredWaitingConfirmationTrade(sellerUserId, buyerUserId, 10_000L);
        pointService.hold(buyerUserId, 10_000L, RefType.BID, trade.bidId(), "자동 완료 테스트 홀딩");
        pointService.convertHoldToEscrow(
                buyerUserId,
                RefType.BID,
                trade.bidId(),
                "자동 완료 테스트 보관금 전환");

        // 운영 설정과 무관하게 이 롤백 테스트 안에서만 배치 조건을 충족시킨다.
        jdbc.update("UPDATE SYSTEM_SETTING SET SYS_SET_AUTO_CMPL_YN = 'Y'");
        TradeAutoCompletionScheduler scheduler = new TradeAutoCompletionScheduler(
                systemSettingMapper,
                tradeMapper,
                tradeService,
                true);

        scheduler.completeExpiredTrades();
        scheduler.completeExpiredTrades();

        assertThat(jdbc.queryForObject(
                "SELECT TRD_STATUS_CD FROM TRADE WHERE TRD_SN = ?", String.class, trade.tradeId()))
                .isEqualTo("TRDC0006");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM SETTLEMENT WHERE TRD_SN = ?", Integer.class, trade.tradeId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT STLM_STATUS_CD FROM SETTLEMENT WHERE TRD_SN = ?", String.class, trade.tradeId()))
                .isEqualTo("STLC0003");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM TRADE_STATUS_HIST WHERE TRD_SN = ? AND TRD_STS_HST_STATUS_CD = 'TRDC0006'",
                Integer.class,
                trade.tradeId()))
                .isEqualTo(1);
        assertThat(notificationService.getUnreadCount(buyerUserId)).isEqualTo(1);
        assertThat(notificationService.getUnreadCount(sellerUserId)).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT COALESCE(SUM(PT_LDG_AMT), 0) FROM POINT_LEDGER WHERE USR_SN = ? AND PT_LDG_PT_TYPE_CD = 'PTLC0003'",
                Long.class,
                sellerUserId)).isEqualTo(10_000L);
    }

    private long insertUser(String prefix) {
        String loginId = prefix + "_" + System.nanoTime();
        String email = loginId + "@test.local";
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML_ENC, USR_EML_HMAC, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', ?, ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, prefix, fieldCryptoService.encrypt(email), fieldCryptoService.emailHmac(email));
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertAvailablePoint(long userId, long amount) {
        jdbc.update("""
                INSERT INTO POINT_LEDGER (
                    USR_SN, PT_LDG_PT_TYPE_CD, PT_LDG_TYPE_CD, PT_LDG_AMT, PT_LDG_BAL_AFTER_AMT, PT_LDG_RSN_CN
                )
                VALUES (?, 'PTLC0001', 'PTLC0004', ?, ?, '자동 완료 테스트 시작 포인트')
                """, userId, amount, amount);
    }

    private MaterialTradeFixture insertExpiredWaitingConfirmationTrade(long sellerUserId, long buyerUserId, long amount) {
        jdbc.update("""
                INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_STATUS_CD, PRD_START_AMT, PRD_TRD_METHOD_CD)
                VALUES (?, 2, '자동 완료 테스트 상품', 'PRDC0003', ?,
                        (SELECT C.CMM_CD FROM CMM_CODE C
                         JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'TRDG03' ORDER BY C.CMM_SORT_NO LIMIT 1))
                """, sellerUserId, amount);
        long productId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("""
                INSERT INTO AUCTION (PRD_SN, AUC_STATUS_CD, AUC_CUR_AMT, AUC_BID_UNIT_AMT, AUC_START_DT, AUC_END_DT, AUC_EXT_CNT)
                VALUES (?, 'AUCC0003', ?, 1000, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 1 HOUR), 0)
                """, productId, amount);
        long auctionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("""
                INSERT INTO BID (AUC_SN, USR_SN, BID_AMT, BID_STATUS_CD)
                VALUES (?, ?, ?, 'BIDC0001')
                """, auctionId, buyerUserId, amount);
        long bidId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("""
                INSERT INTO TRADE (
                    TRD_TYPE_CD, TRD_STATUS_CD, TRD_AMT, SLLR_USR_SN, BYPR_USR_SN, PRD_SN, BID_SN, TRD_AUTO_CMPL_DT
                )
                VALUES ('TRDC0001', 'TRDC0005', ?, ?, ?, ?, ?, DATE_SUB(NOW(), INTERVAL 1 MINUTE))
                """, amount, sellerUserId, buyerUserId, productId, bidId);
        long tradeId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return new MaterialTradeFixture(tradeId, bidId);
    }

    private record MaterialTradeFixture(long tradeId, long bidId) {
    }
}
