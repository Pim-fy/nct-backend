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

import nct.point.domain.PointChargeOrderStatus;
import nct.point.dto.PointChargeOrderResponse;
import nct.point.exception.PointException;
import nct.point.service.PointChargeService;

/**
 * Claude Code 작성 (BJN, 2026-07-16)
 *
 * [테스트 - 포인트 충전 주문 생성·이력 조회] (F-PAY-011)
 *
 * 공유 DB(NCTDB) 주의사항 — PointFlowTest와 동일:
 * - @Transactional 테스트는 메소드 종료 시 전부 롤백되어 행을 남기지 않는다
 * - 테스트 회원은 매 실행 nanoTime으로 유니크하게 생성되어 팀원 데이터와 충돌하지 않는다
 *
 * confirm(토스 승인 호출)은 외부 API가 필요하므로 여기서는 다루지 않고,
 * 주문 생성 → 이력 조회(상태 한글명 조인 포함) 계약만 검증한다.
 */
@SpringBootTest
@Transactional
class PointChargeOrderTest {

    @Autowired PointChargeService pointChargeService;
    @Autowired JdbcTemplate jdbc;

    long usrSn;

    @BeforeEach
    void setUpUser() {
        String loginId = "t_charge_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, loginId, loginId + "@test.local");
        usrSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    @DisplayName("주문 생성: 대기 상태로 기록되고 이력 조회에 상태 한글명과 함께 나타난다")
    void createOrderAndList() {
        String orderNo = pointChargeService.createOrder(usrSn, 50000);

        assertThat(orderNo).startsWith("CHG-" + usrSn + "-");
        assertThat(pointChargeService.getOrderList(usrSn))
                .singleElement()
                .satisfies(o -> {
                    assertThat(o.getPtChgOrdNo()).isEqualTo(orderNo);
                    assertThat(o.getPtChgOrdAmt()).isEqualTo(50000);
                    assertThat(o.getPtChgOrdStatusCd()).isEqualTo(PointChargeOrderStatus.PENDING.getCode());
                    // CMM_CODE 조인으로 채우는 한글명 — 기초데이터 PCOC0001='대기'
                    assertThat(o.getStatusNm()).isEqualTo("대기");
                });
    }

    @Test
    @DisplayName("주문 생성: 금액이 0 이하이면 거부하고 이력에 흔적을 남기지 않는다")
    void createOrderNonPositiveAmount() {
        assertThatThrownBy(() -> pointChargeService.createOrder(usrSn, 0))
                .isInstanceOf(PointException.class);
        assertThatThrownBy(() -> pointChargeService.createOrder(usrSn, -10000))
                .isInstanceOf(PointException.class);

        assertThat(pointChargeService.getOrderList(usrSn)).isEmpty();
    }

    @Test
    @DisplayName("주문 생성: SYSTEM_SETTING 충전 한도를 벗어나면 거부하고 이력에 흔적을 남기지 않는다")
    void createOrderOutOfChargeLimit() {
        // 기초데이터 기준 SYS_SET_MIN_CHRG_AMT=10,000 / SYS_SET_MAX_CHRG_AMT=1,000,000 (D-010 단일 row)
        assertThatThrownBy(() -> pointChargeService.createOrder(usrSn, 9_000))
                .isInstanceOf(PointException.class);
        assertThatThrownBy(() -> pointChargeService.createOrder(usrSn, 1_000_001))
                .isInstanceOf(PointException.class);

        assertThat(pointChargeService.getOrderList(usrSn)).isEmpty();
    }

    @Test
    @DisplayName("만료(D-025): 3시간 지난 대기 주문은 승인이 거부된다")
    void confirmExpiredPendingOrder() {
        String orderNo = pointChargeService.createOrder(usrSn, 50000);
        backdateOrder(orderNo, 4); // 4시간 전 생성으로 되돌림 → 유효시간(3시간) 초과

        // 만료 검사는 토스 승인 호출보다 먼저 수행되므로 외부 API 없이 검증 가능
        assertThatThrownBy(() -> pointChargeService.confirm(orderNo, "dummy-payment-key"))
                .isInstanceOf(PointException.class)
                .hasMessageContaining("만료");
    }

    @Test
    @DisplayName("만료(D-025): 3시간 지난 대기 건은 이력에서 취소(시간 만료)로 표시된다")
    void listMarksExpiredPendingAsCanceled() {
        String freshNo = pointChargeService.createOrder(usrSn, 20000);
        String expiredNo = pointChargeService.createOrder(usrSn, 50000);
        backdateOrder(expiredNo, 4);

        var orders = pointChargeService.getOrderList(usrSn);
        assertThat(orders).hasSize(2);

        var expired = orders.stream().filter(o -> o.getPtChgOrdNo().equals(expiredNo)).findFirst().orElseThrow();
        assertThat(expired.getPtChgOrdStatusCd()).isEqualTo(PointChargeOrderStatus.CANCELED.getCode());
        assertThat(expired.getStatusNm()).isEqualTo("취소");
        assertThat(expired.getPtChgOrdFailRsnCn()).contains("시간 만료");

        // 3시간이 안 지난 대기 건은 그대로 대기로 보인다
        var fresh = orders.stream().filter(o -> o.getPtChgOrdNo().equals(freshNo)).findFirst().orElseThrow();
        assertThat(fresh.getPtChgOrdStatusCd()).isEqualTo(PointChargeOrderStatus.PENDING.getCode());
        assertThat(fresh.getStatusNm()).isEqualTo("대기");
    }

    /** 주문 생성 시각을 과거로 되돌린다 — 시간 경과를 기다릴 수 없으니 테스트에서만 쓰는 조작 (롤백됨) */
    private void backdateOrder(String orderNo, int hoursAgo) {
        jdbc.update("UPDATE POINT_CHARGE_ORDER SET PT_CHG_ORD_REG_DT = DATE_SUB(NOW(), INTERVAL ? HOUR) WHERE PT_CHG_ORD_NO = ?",
                hoursAgo, orderNo);
    }

    @Test
    @DisplayName("이력 조회: 최신순 정렬, 응답 DTO 변환까지 프론트 계약대로 채워진다")
    void listOrderingAndDtoMapping() {
        pointChargeService.createOrder(usrSn, 10000);
        String latest = pointChargeService.createOrder(usrSn, 30000);

        var orders = pointChargeService.getOrderList(usrSn);
        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).getPtChgOrdNo()).isEqualTo(latest); // 최신 건이 먼저

        PointChargeOrderResponse dto = PointChargeOrderResponse.from(orders.get(0));
        assertThat(dto.getOrderNo()).isEqualTo(latest);
        assertThat(dto.getAmount()).isEqualTo(30000);
        assertThat(dto.getStatus()).isEqualTo("대기");
        assertThat(dto.getStatusCd()).isEqualTo(PointChargeOrderStatus.PENDING.getCode());
        assertThat(dto.getFailReason()).isNull();
        assertThat(dto.getDate()).isNotBlank();
    }
}
