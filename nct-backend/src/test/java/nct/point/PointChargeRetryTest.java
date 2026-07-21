package nct.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import nct.global.exception.ErrorCode;
import nct.point.client.TossConfirmResult;
import nct.point.client.TossPaymentsClient;
import nct.point.domain.PointChargeOrderStatus;
import nct.point.exception.PointException;
import nct.point.service.PointChargeService;

/**
 * Claude Code 작성 (BJN, 2026-07-21)
 *
 * [테스트 - F-PG-04 결제 승인 자동 재시도]
 *
 * 통신 실패(EXTERNAL_API_ERROR)만 지수 백오프로 재시도되고, 토스가 명시적으로 거절한
 * 비즈니스 오류는 재시도 없이 즉시 실패해야 한다. 토스 클라이언트만 가짜로 바꿔 재현한다 —
 * 실제 외부 호출 없음. 공유 DB(NCTDB) 주의: @Transactional 테스트라 종료 시 전부 롤백.
 */
@SpringBootTest
@Transactional
class PointChargeRetryTest {

    @Autowired PointChargeService pointChargeService;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean TossPaymentsClient tossPaymentsClient;

    long usrSn;

    @BeforeEach
    void setUpUser() {
        String loginId = "t_retry_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, loginId, loginId + "@test.local");
        usrSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    @DisplayName("통신 실패가 2회 이어져도 3번째 시도에서 성공하면 정상 지급된다")
    void retriesUntilSuccess() {
        String orderNo = pointChargeService.createOrder(usrSn, 50000);
        when(tossPaymentsClient.confirm(anyString(), anyString(), anyLong()))
                .thenThrow(new PointException(ErrorCode.EXTERNAL_API_ERROR, "일시 통신 오류"))
                .thenThrow(new PointException(ErrorCode.EXTERNAL_API_ERROR, "일시 통신 오류"))
                .thenReturn(TossConfirmResult.success(50000));

        pointChargeService.confirm(orderNo, "test-payment-key");

        verify(tossPaymentsClient, times(3)).confirm(anyString(), anyString(), anyLong());
        var order = pointChargeService.getOrderList(usrSn).get(0);
        assertThat(order.getPtChgOrdStatusCd()).isEqualTo(PointChargeOrderStatus.COMPLETED.getCode());
    }

    @Test
    @DisplayName("통신 실패가 재시도 한도(최대 3회)를 넘기면 예외로 실패하되, 주문은 '대기'로 남는다")
    void failsAfterExhaustingRetries() {
        // 통신 실패는 토스 쪽 실제 성공 여부를 알 수 없는 애매한 상태라, 여기서 '실패'로
        // 단정 기록하지 않는다 — 대기로 남겨야 2단계 대사 배치가 나중에 토스에 직접 확인해
        // "사실 결제는 성공했더라"를 복구할 수 있다. 여기서 FAILED로 확정해버리면 그 복구
        // 경로 자체가 막힌다.
        String orderNo = pointChargeService.createOrder(usrSn, 50000);
        when(tossPaymentsClient.confirm(anyString(), anyString(), anyLong()))
                .thenThrow(new PointException(ErrorCode.EXTERNAL_API_ERROR, "일시 통신 오류"));

        assertThatThrownBy(() -> pointChargeService.confirm(orderNo, "test-payment-key"))
                .isInstanceOf(PointException.class);

        // 최초 시도 1회 + 재시도 3회 = 4회
        verify(tossPaymentsClient, times(4)).confirm(anyString(), anyString(), anyLong());
        var order = pointChargeService.getOrderList(usrSn).get(0);
        assertThat(order.getPtChgOrdStatusCd()).isEqualTo(PointChargeOrderStatus.PENDING.getCode());
    }

    @Test
    @DisplayName("비즈니스 거절(카드 한도초과 등)은 재시도 없이 즉시 실패한다")
    void businessRejectionSkipsRetry() {
        String orderNo = pointChargeService.createOrder(usrSn, 50000);
        when(tossPaymentsClient.confirm(anyString(), anyString(), anyLong()))
                .thenReturn(TossConfirmResult.failure("카드 한도 초과"));

        assertThatThrownBy(() -> pointChargeService.confirm(orderNo, "test-payment-key"))
                .isInstanceOf(PointException.class);

        verify(tossPaymentsClient, times(1)).confirm(anyString(), anyString(), anyLong());
    }
}
