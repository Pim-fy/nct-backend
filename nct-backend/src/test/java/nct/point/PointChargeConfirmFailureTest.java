package nct.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;

import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import nct.notification.service.NotificationService;
import nct.point.client.TossConfirmResult;
import nct.point.client.TossPaymentsClient;
import nct.point.domain.PointChargeOrderStatus;
import nct.point.exception.PointException;
import nct.point.service.PointChargeService;

/**
 * Claude Code 작성 (BJN, 2026-07-17)
 *
 * [테스트 - 충전 승인 실패 시 실패 상태가 DB에 남는가] (noRollbackFor 회귀 검증)
 *
 * 배경: 승인 거절·금액 불일치 때 "실패 기록 → 예외" 순서로 처리하는데, 예외로 트랜잭션이
 * 롤백되면 방금 기록한 실패 상태까지 사라져 주문이 영영 '대기'로 남는 문제가 있었다.
 * confirm에 noRollbackFor(PointException)를 지정해 실패 기록은 남기도록 고쳤고,
 * 이 테스트는 그 동작이 다시 깨지지 않는지 지킨다.
 *
 * 토스 클라이언트만 가짜(Mock)로 바꿔 승인 거절·금액 불일치를 재현한다 — 실제 외부 호출 없음.
 * 공유 DB(NCTDB) 주의: @Transactional 테스트라 종료 시 전부 롤백, 행을 남기지 않는다.
 */
@SpringBootTest
@Transactional
class PointChargeConfirmFailureTest {

    @Autowired PointChargeService pointChargeService;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    /** 실제 토스 API 대신 정해진 응답을 돌려주는 가짜 클라이언트 */
    @MockitoBean TossPaymentsClient tossPaymentsClient;

    /** 알림 저장을 가짜로 바꿔 "승인 후 내부 반영 실패"(D-027 보상 경로)를 재현하는 데 쓴다 */
    @MockitoBean NotificationService notificationService;

    long usrSn;

    @BeforeEach
    void setUpUser() {
        String loginId = "t_cfail_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, loginId, loginId + "@test.local");
        usrSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    @DisplayName("승인 거절: 예외가 나도 주문이 '실패' 상태와 사유로 남는다 (대기로 되돌아가지 않음)")
    void rejectedConfirmPersistsFailedStatus() {
        String orderNo = pointChargeService.createOrder(usrSn, 50000);
        when(tossPaymentsClient.confirm(anyString(), anyString(), anyLong()))
                .thenReturn(TossConfirmResult.failure("카드 한도 초과"));

        assertThatThrownBy(() -> pointChargeService.confirm(orderNo, "test-payment-key"))
                .isInstanceOf(PointException.class);

        // 핵심 검증: noRollbackFor가 없으면 예외가 트랜잭션에 rollback-only 표식을 남겨
        // 실패 기록이 커밋 시점에 통째로 사라진다 — 표식이 없어야 실패 기록이 실제로 남는 것
        assertThat(isTransactionRollbackOnly())
                .as("PointException이 트랜잭션을 롤백 대상으로 만들면 실패 기록이 사라진다")
                .isFalse();

        var order = pointChargeService.getOrderList(usrSn).get(0);
        assertThat(order.getPtChgOrdStatusCd()).isEqualTo(PointChargeOrderStatus.FAILED.getCode());
        assertThat(order.getPtChgOrdFailRsnCn()).contains("한도 초과");
    }

    @Test
    @DisplayName("금액 불일치: '실패' 상태·사유가 남고 포인트는 지급되지 않는다")
    void mismatchPersistsFailedStatusWithoutCredit() {
        String orderNo = pointChargeService.createOrder(usrSn, 50000);
        // 사전 기록(50,000)과 다른 금액(49,000)이 승인된 상황 — 위변조 의심 케이스
        when(tossPaymentsClient.confirm(anyString(), anyString(), anyLong()))
                .thenReturn(TossConfirmResult.success(49000));

        assertThatThrownBy(() -> pointChargeService.confirm(orderNo, "test-payment-key"))
                .isInstanceOf(PointException.class);

        // 실패 기록이 롤백 대상으로 묶이지 않았는지 확인 (위 테스트와 동일한 핵심 검증)
        assertThat(isTransactionRollbackOnly()).isFalse();

        var order = pointChargeService.getOrderList(usrSn).get(0);
        assertThat(order.getPtChgOrdStatusCd()).isEqualTo(PointChargeOrderStatus.FAILED.getCode());
        assertThat(order.getPtChgOrdFailRsnCn()).contains("불일치");

        // 포인트 원장이 생기지 않았어야 한다 (실패인데 지급되면 안 됨)
        Long ledgerCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM POINT_LEDGER WHERE USR_SN = ?", Long.class, usrSn);
        assertThat(ledgerCount).isZero();
    }

    @Test
    @DisplayName("보상(D-027): 승인 후 내부 반영 실패 시 자동 결제취소하고 지급분을 회수한다")
    void internalFailureTriggersAutoCancelAndReversal() {
        String orderNo = pointChargeService.createOrder(usrSn, 50000);
        when(tossPaymentsClient.confirm(anyString(), anyString(), anyLong()))
                .thenReturn(TossConfirmResult.success(50000));
        when(tossPaymentsClient.cancel(anyString(), anyString())).thenReturn(true); // ⓐ 자동취소 성공
        // 지급·완료까지 끝난 뒤 마지막 단계(알림 저장)에서 터지는 상황 재현
        doThrow(new RuntimeException("알림 저장 실패"))
                .when(notificationService).notifyCharge(anyLong(), anyLong());

        assertThatThrownBy(() -> pointChargeService.confirm(orderNo, "test-payment-key"))
                .isInstanceOf(PointException.class);

        // 보상 기록이 롤백 대상으로 묶이지 않았어야 한다
        assertThat(isTransactionRollbackOnly()).isFalse();

        // 결제취소가 실제로 호출됐는지
        verify(tossPaymentsClient).cancel(anyString(), anyString());

        // 주문은 실패 + "자동취소 완료" 사유
        var order = pointChargeService.getOrderList(usrSn).get(0);
        assertThat(order.getPtChgOrdStatusCd()).isEqualTo(PointChargeOrderStatus.FAILED.getCode());
        assertThat(order.getPtChgOrdFailRsnCn()).contains("자동취소 완료");

        // 원장: 지급(+50000)과 회수(-50000) 두 행이 짝으로 남고 합계는 0 — 잔액 원상복구
        Long ledgerCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM POINT_LEDGER WHERE USR_SN = ?", Long.class, usrSn);
        Long ledgerSum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(PT_LDG_AMT),0) FROM POINT_LEDGER WHERE USR_SN = ?", Long.class, usrSn);
        assertThat(ledgerCount).isEqualTo(2);
        assertThat(ledgerSum).isZero();
    }

    @Test
    @DisplayName("보상(D-027): 자동취소마저 실패하면 '관리자 확인 필요' 사유로 남긴다")
    void cancelFailureLeavesAdminNote() {
        String orderNo = pointChargeService.createOrder(usrSn, 50000);
        when(tossPaymentsClient.confirm(anyString(), anyString(), anyLong()))
                .thenReturn(TossConfirmResult.success(50000));
        when(tossPaymentsClient.cancel(anyString(), anyString())).thenReturn(false); // ⓑ 취소 실패
        doThrow(new RuntimeException("알림 저장 실패"))
                .when(notificationService).notifyCharge(anyLong(), anyLong());

        assertThatThrownBy(() -> pointChargeService.confirm(orderNo, "test-payment-key"))
                .isInstanceOf(PointException.class);

        var order = pointChargeService.getOrderList(usrSn).get(0);
        assertThat(order.getPtChgOrdStatusCd()).isEqualTo(PointChargeOrderStatus.FAILED.getCode());
        assertThat(order.getPtChgOrdFailRsnCn()).contains("관리자 확인 필요");
    }

    /**
     * 지금 트랜잭션에 rollback-only(커밋 시 전부 무효) 표식이 붙었는지 확인.
     * 테스트와 서비스가 같은 트랜잭션을 공유하므로, confirm 안에서 표식이 붙었다면 여기서 보인다.
     */
    private boolean isTransactionRollbackOnly() {
        ConnectionHolder holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
        return holder != null && holder.isRollbackOnly();
    }
}
