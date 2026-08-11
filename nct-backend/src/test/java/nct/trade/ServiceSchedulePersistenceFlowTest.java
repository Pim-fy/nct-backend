package nct.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import nct.global.security.crypto.FieldCryptoService;
import nct.trade.dto.ServiceScheduleCancellationCommand;
import nct.trade.dto.ServiceScheduleChangeCommand;
import nct.trade.dto.ServiceTradeDetailResponse;
import nct.trade.mapper.TradeMapper;
import nct.trade.service.TradeService;

/**
 * F-SVC-016의 상태 이력 저장·조회 계약을 실제 MySQL에서 검증한다.
 * 테스트 데이터는 메서드 종료 뒤 트랜잭션 롤백으로 공용 DB에 남지 않는다.
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@Transactional
class ServiceSchedulePersistenceFlowTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FieldCryptoService fieldCryptoService;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private TradeMapper tradeMapper;

    private long requesterUserId;
    private long providerUserId;
    private long tradeId;

    @BeforeEach
    void setUp() {
        requesterUserId = insertUser("schedule_requester");
        providerUserId = insertUser("schedule_provider");
        tradeId = insertServiceTrade();
    }

    @Test
    void storesScheduleRequestsAndReturnsThemInServiceTradeDetail() {
        LocalDateTime requestedScheduleAt = LocalDateTime.now().plusDays(2).withNano(0);

        tradeService.requestServiceScheduleChange(
                tradeId,
                requesterUserId,
                new ServiceScheduleChangeCommand(requestedScheduleAt, "  고객 요청으로 오후 일정으로 변경합니다.  "));
        tradeService.requestServiceScheduleCancellation(
                tradeId,
                providerUserId,
                new ServiceScheduleCancellationCommand("장비 점검으로 일정 취소를 요청합니다."));

        ServiceTradeDetailResponse detail = tradeService.getMyServiceTradeDetail(tradeId, requesterUserId);

        assertThat(jdbc.queryForObject(
                "SELECT TRD_STATUS_CD FROM TRADE WHERE TRD_SN = ?", String.class, tradeId))
                .isEqualTo("TRDC0003");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM SETTLEMENT WHERE TRD_SN = ?", Integer.class, tradeId))
                .isZero();
        assertThat(tradeMapper.findServiceScheduleHistory(tradeId))
                .extracting(item -> item.eventType())
                .containsExactly("CANCEL_REQUEST", "CHANGE");
        assertThat(detail.scheduleHistory()).hasSize(2);
        assertThat(detail.scheduleHistory().get(0).reason())
                .isEqualTo("장비 점검으로 일정 취소를 요청합니다.");
        assertThat(detail.scheduleHistory().get(0).actorRole()).isEqualTo("PROVIDER");
        assertThat(detail.scheduleHistory().get(1).requestedScheduleAt())
                .isEqualTo(requestedScheduleAt);
        assertThat(detail.scheduleHistory().get(1).reason())
                .isEqualTo("고객 요청으로 오후 일정으로 변경합니다.");
        assertThat(detail.scheduleHistory().get(1).actorRole()).isEqualTo("REQUESTER");
    }

    @Test
    void returnsDisputeReceiptWithoutExposingReportedContent() {
        tradeMapper.insertStatusHistory(tradeId, "TRDC0007", "거래 문제가 접수되었습니다.");

        ServiceTradeDetailResponse detail = tradeService.getMyServiceTradeDetail(tradeId, providerUserId);

        assertThat(detail.scheduleHistory()).singleElement().satisfies(item -> {
            assertThat(item.eventType()).isEqualTo("DISPUTE_REPORTED");
            assertThat(item.reason()).isEqualTo("거래 문제가 접수되어 거래와 정산이 보류되었습니다.");
        });
    }

    @Test
    void returnsEscrowLifecycleHistoryInNewestFirstOrder() {
        tradeMapper.insertStatusHistory(tradeId, "TRDC0003", "선택 견적으로 서비스 거래가 생성되었습니다.");
        tradeMapper.insertStatusHistory(tradeId, "TRDC0005", "SERVICE_COMPLETION_REQUEST|완료 요청 메모는 이력에 공개하지 않습니다.");
        tradeMapper.insertStatusHistory(tradeId, "TRDC0006", "서비스 의뢰자가 완료를 확인했습니다.");
        tradeMapper.insertStatusHistory(tradeId, "TRDC0008", "관리자 환불 처리 사유는 이력에 공개하지 않습니다.");

        assertThat(tradeMapper.findServiceScheduleHistory(tradeId))
                .extracting(item -> item.eventType())
                .containsExactly("ESCROW_REFUNDED", "SETTLEMENT_COMPLETED", "COMPLETION_REQUESTED", "ESCROW_HELD");
        assertThat(tradeMapper.findServiceScheduleHistory(tradeId))
                .extracting(item -> item.reason())
                .containsExactly(
                        "거래가 취소되어 보관금이 환불되었습니다.",
                        "거래가 완료되어 정산이 완료되었습니다.",
                        "서비스 완료 요청이 등록되어 의뢰자 확인을 기다립니다.",
                        "선택 견적이 확정되어 보관금이 예치되었습니다.");
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

    private long insertServiceTrade() {
        jdbc.update("""
                INSERT INTO SERVICE_REQUEST (USR_SN, CAT_SN, SVC_REQ_TTL, SVC_REQ_STATUS_CD)
                VALUES (?, 2, '일정 이력 통합 테스트 요청',
                        (SELECT C.CMM_CD FROM CMM_CODE C
                         JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'SVCG01' ORDER BY C.CMM_SORT_NO LIMIT 1))
                """, requesterUserId);
        long serviceRequestId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("""
                INSERT INTO QUOTE (SVC_REQ_SN, USR_SN, QUT_AMT, QUT_STATUS_CD)
                VALUES (?, ?, 30000,
                        (SELECT C.CMM_CD FROM CMM_CODE C
                         JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'QUTG01' ORDER BY C.CMM_SORT_NO LIMIT 1))
                """, serviceRequestId, providerUserId);
        long quoteId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update("""
                INSERT INTO TRADE (TRD_TYPE_CD, TRD_STATUS_CD, TRD_AMT, REQ_USR_SN, PRV_USR_SN, SVC_REQ_SN, QUT_SN)
                VALUES ('TRDC0002', 'TRDC0003', 30000, ?, ?, ?, ?)
                """, requesterUserId, providerUserId, serviceRequestId, quoteId);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
