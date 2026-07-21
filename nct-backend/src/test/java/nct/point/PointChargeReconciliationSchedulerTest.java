package nct.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import nct.ops.risk.service.RiskEventCommand;
import nct.ops.risk.service.RiskEventResult;
import nct.ops.risk.service.RiskEventService;
import nct.point.client.TossOrderLookupResult;
import nct.point.client.TossPaymentsClient;
import nct.point.domain.PointChargeOrderStatus;
import nct.point.scheduler.PointChargeReconciliationScheduler;
import nct.point.service.PointChargeService;

/**
 * Claude Code 작성 (BJN, 2026-07-21)
 *
 * [테스트 - 2단계 confirm 콜백 유실 방어 배치]
 *
 * 토스 클라이언트만 가짜로 바꿔 "결제완료 확인/결제 이력 없음/통신 실패" 세 응답을 재현한다 —
 * 실제 외부 호출 없음. 상태 확인은 getOrderList()가 아니라 DB를 직접 조회한다 — getOrderList는
 * TTL이 지난 PENDING 건을 화면용으로 "취소"라고 덧씌워 보여주므로(markExpiredForDisplay),
 * 실제 DB 상태를 검증하는 이 테스트의 목적과 맞지 않는다.
 * 공유 DB(NCTDB) 주의: @Transactional 테스트라 종료 시 전부 롤백.
 *
 * point.reconciliation.scheduler.enabled를 이 클래스에서만 true로 되돌린다 — 다른 모든
 * 테스트에서는 기본 꺼짐(src/test/resources/application.properties)이라 스케줄 자동실행으로
 * 실DB가 건드려지지 않는다.
 */
@SpringBootTest
@TestPropertySource(properties = "point.reconciliation.scheduler.enabled=true")
@Transactional
class PointChargeReconciliationSchedulerTest {

    @Autowired PointChargeReconciliationScheduler scheduler;
    @Autowired PointChargeService pointChargeService;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean TossPaymentsClient tossPaymentsClient;

    // 실제 RiskEventService를 타면 DB에 영구히 남는다(2026-07-21 확인 — @Transactional
    // 테스트 롤백이 이 경로엔 왜인지 적용되지 않아 공유 DB에 위험 이벤트 잔여물이 실제로
    // 남았던 사고). 다른 기존 테스트들(SensitiveDataInspectionServiceTest 등)도 전부
    // RiskEventService를 가짜로 바꿔 쓰는 게 이 코드베이스의 관례라 그대로 따른다.
    @MockitoBean RiskEventService riskEventService;

    long usrSn;

    @BeforeEach
    void setUpUser() {
        String loginId = "t_recon_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, loginId, loginId + "@test.local");
        usrSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // 공유 DB에 이 테스트가 만들지 않은 다른 오래된 PENDING 주문이 이미 있을 수 있다 —
        // 배치는 그 주문들까지 함께 훑으므로, 내 테스트 주문 외에는 안전하게 "통신 실패"로
        // 응답해 건드리지 않게 기본값을 깔아둔다. 각 테스트가 자기 주문번호로는 더 구체적인
        // 응답을 뒤이어 스텁하며, Mockito는 나중에 등록된(더 구체적인) 스텁을 우선한다.
        when(tossPaymentsClient.lookupByOrderId(anyString()))
                .thenReturn(TossOrderLookupResult.unreachable());
        when(riskEventService.recordOnce(any(RiskEventCommand.class)))
                .thenReturn(new RiskEventResult(1L, true));
    }

    /** 방금 생성한 주문의 등록시각을 과거로 되돌려 "오래된 PENDING 건"을 재현한다 */
    private String createStaleOrder(long amt, Duration age) {
        String orderNo = pointChargeService.createOrder(usrSn, amt);
        jdbc.update("UPDATE POINT_CHARGE_ORDER SET PT_CHG_ORD_REG_DT = ? WHERE PT_CHG_ORD_NO = ?",
                LocalDateTime.now().minus(age), orderNo);
        return orderNo;
    }

    private String statusOf(String orderNo) {
        return jdbc.queryForObject(
                "SELECT PT_CHG_ORD_STATUS_CD FROM POINT_CHARGE_ORDER WHERE PT_CHG_ORD_NO = ?",
                String.class, orderNo);
    }

    @Test
    @DisplayName("토스가 결제완료(DONE)를 확인해주면 confirm 콜백 없이도 자동 지급된다")
    void recoversDonePaymentAutomatically() {
        String orderNo = createStaleOrder(50000, Duration.ofMinutes(20));
        when(tossPaymentsClient.lookupByOrderId(orderNo))
                .thenReturn(TossOrderLookupResult.found("DONE", 50000, "recovered-payment-key"));

        scheduler.reconcilePendingOrders();

        assertThat(statusOf(orderNo)).isEqualTo(PointChargeOrderStatus.COMPLETED.getCode());
        Long ledgerCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM POINT_LEDGER WHERE USR_SN = ?", Long.class, usrSn);
        assertThat(ledgerCount).isEqualTo(1);
        verify(riskEventService).recordOnce(any(RiskEventCommand.class));
    }

    @Test
    @DisplayName("결제 이력이 없고 TTL(3시간)도 지나면 실패로 확정된다")
    void expiresOrderWithNoPaymentHistoryPastTtl() {
        String orderNo = createStaleOrder(30000, Duration.ofHours(4));
        when(tossPaymentsClient.lookupByOrderId(orderNo))
                .thenReturn(TossOrderLookupResult.notFound());

        scheduler.reconcilePendingOrders();

        assertThat(statusOf(orderNo)).isEqualTo(PointChargeOrderStatus.FAILED.getCode());
    }

    @Test
    @DisplayName("아직 TTL 이내면 결제 이력이 안 보여도 대기 상태를 그대로 둔다")
    void leavesRecentOrderPendingWhenNotYetDone() {
        String orderNo = createStaleOrder(20000, Duration.ofMinutes(30));
        when(tossPaymentsClient.lookupByOrderId(orderNo))
                .thenReturn(TossOrderLookupResult.notFound());

        scheduler.reconcilePendingOrders();

        assertThat(statusOf(orderNo)).isEqualTo(PointChargeOrderStatus.PENDING.getCode());
    }

    @Test
    @DisplayName("토스 통신 실패면 TTL이 지났어도 확정하지 않고 다음 주기로 넘긴다")
    void skipsWhenTossUnreachable() {
        String orderNo = createStaleOrder(20000, Duration.ofHours(4));
        when(tossPaymentsClient.lookupByOrderId(orderNo))
                .thenReturn(TossOrderLookupResult.unreachable());

        scheduler.reconcilePendingOrders();

        assertThat(statusOf(orderNo)).isEqualTo(PointChargeOrderStatus.PENDING.getCode());
    }
}
