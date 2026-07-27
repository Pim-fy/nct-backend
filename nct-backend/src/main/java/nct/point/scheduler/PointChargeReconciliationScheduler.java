package nct.point.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nct.ops.risk.service.RiskEventCommand;
import nct.ops.risk.service.RiskEventService;
import nct.point.client.TossOrderLookupResult;
import nct.point.client.TossPaymentsClient;
import nct.point.domain.PointChargeOrder;
import nct.point.exception.PointException;
import nct.point.mapper.PointChargeOrderMapper;
import nct.point.service.PointChargeService;

/**
 * Claude Code 작성 (BJN, 2026-07-21)
 *
 * [confirm 콜백 유실 방어 — 사후 대사 배치] (QSC-PG-06 연장)
 *
 * 사용자가 토스 결제창에서 결제를 마쳤는데 프론트가 confirm()을 호출하기 전에 이탈하면
 * (새로고침·브라우저 종료·네트워크 끊김), 서버는 그 결제를 영원히 모른 채 주문이 PENDING으로
 * 남는다. 이 배치가 주기적으로 오래된 PENDING 주문을 훑어 토스에 직접 물어본다:
 * - 결제완료(DONE)로 확인되면 → 자동 지급(사용자 확정, 2026-07-21) + 위험이벤트 기록으로 투명하게 남김
 * - 결제 이력 자체가 없거나 취소·만료 상태이고 TTL(3시간)도 지났으면 → 실패로 확정
 * - 그 외(진행중이거나 아직 TTL 이내)는 다음 주기에 다시 확인
 *
 * D-025("대기 주문 만료는 배치 없이 판정 시점에 처리")를 뒤집는 게 아니다 — D-025는
 * "오래된 PENDING을 취소 처리할지"에 대한 결정이고, 이 배치는 "PENDING인데 실제로는
 * 토스에서 결제가 끝난 건을 찾아내는" 별개 문제를 다룬다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
// 기본값 꺼짐(matchIfMissing=false) — application.properties(git 미추적)에
// point.reconciliation.scheduler.enabled=true 를 직접 추가해야 켜진다. 컨텍스트가 뜨자마자
// 실제 토스 API 호출 + 실DB 쓰기가 일어나는 배치라, 값을 명시적으로 넣지 않은 환경(테스트 등)
// 에서는 조용히 꺼져 있는 게 안전하다 (2026-07-21: 기본 켜짐으로 뒀다가 테스트 실행만으로
// 공유 실DB의 낡은 주문 3건이 실제로 처리된 사고 이후 이 정책으로 변경).
@ConditionalOnProperty(prefix = "point.reconciliation.scheduler", name = "enabled", havingValue = "true", matchIfMissing = false)
public class PointChargeReconciliationScheduler {

    /** RSKG01(위험 이벤트 유형) 중 '결제위험' — 정본 기초데이터에 이미 있는 코드, 신규 등록 불필요 */
    private static final String RISK_TYPE_PAYMENT = "RSKC0003";

    /** 결제창 진행에 걸리는 정상 시간을 넘긴 것만 대상으로 삼는다 — 방금 생성된 주문까지 매번 토스에 묻지 않도록 */
    private static final Duration STALE_AFTER = Duration.ofMinutes(10);

    private final PointChargeOrderMapper orderMapper;
    private final TossPaymentsClient tossPaymentsClient;
    private final PointChargeService pointChargeService;
    private final RiskEventService riskEventService;

    @Scheduled(fixedDelay = 900_000) // 15분
    public void reconcile() {
        reconcilePendingOrders();
    }

    /** 실제 로직은 별도 public 메서드로 분리 — 테스트에서 스케줄 대기 없이 직접 호출 가능하도록 */
    public void reconcilePendingOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minus(STALE_AFTER);
        List<PointChargeOrder> staleOrders = orderMapper.selectPendingOlderThan(cutoff);
        if (staleOrders.isEmpty()) {
            return;
        }

        int recovered = 0;
        int expired = 0;
        for (PointChargeOrder order : staleOrders) {
            Outcome outcome;
            try {
                outcome = reconcileOne(order);
            } catch (RuntimeException e) {
                // 한 건에서 예기치 못한 오류가 나도 나머지 대상 건 처리는 계속한다
                log.warn("포인트 충전 대사 배치: 주문 {} 처리 중 예기치 못한 오류 — 건너뜀", order.getPtChgOrdNo(), e);
                outcome = Outcome.SKIPPED;
            }
            switch (outcome) {
                case RECOVERED -> recovered++;
                case EXPIRED -> expired++;
                case SKIPPED -> { /* 다음 주기에 재확인 */ }
            }
        }
        log.info("포인트 충전 대사 배치: 대상 {}건, 자동복구 {}건, 실패확정 {}건", staleOrders.size(), recovered, expired);
    }

    private Outcome reconcileOne(PointChargeOrder order) {
        TossOrderLookupResult lookup = tossPaymentsClient.lookupByOrderId(order.getPtChgOrdNo());

        if (!lookup.reachable()) {
            return Outcome.SKIPPED; // 통신 실패 — 이번엔 확인 못 함, 다음 주기에 재시도
        }

        if (lookup.isDone()) {
            return recoverPaidOrder(order, lookup);
        }

        boolean pastTtl = order.getPtChgOrdRegDt() != null
                && order.getPtChgOrdRegDt().plus(PointChargeService.PENDING_TTL).isBefore(LocalDateTime.now());
        if (!pastTtl) {
            return Outcome.SKIPPED; // 아직 TTL 이내 — 결제창이 진행 중일 수 있음
        }

        String reason = lookup.found()
                ? "배치 대사: 결제 미체결 확인 (토스 상태: " + lookup.status() + ")"
                : "배치 대사: 결제 시도 이력 없음 확인";
        try {
            pointChargeService.expireIfStillPending(order.getPtChgOrdNo(), reason);
        } catch (RuntimeException e) {
            log.warn("포인트 충전 대사 배치: 주문 {} 실패확정 중 오류 — 다음 주기에 재시도", order.getPtChgOrdNo(), e);
            return Outcome.SKIPPED;
        }
        return Outcome.EXPIRED;
    }

    private Outcome recoverPaidOrder(PointChargeOrder order, TossOrderLookupResult lookup) {
        try {
            pointChargeService.recoverFromReconciliation(
                    order.getPtChgOrdNo(), lookup.paymentKey(), lookup.totalAmount());
        } catch (PointException e) {
            // 이미 처리됐거나(사용자가 뒤늦게 confirm 성공) 금액 불일치 등 — 배치가 손댈 문제가
            // 아니므로 조용히 넘어간다(전자는 정상, 후자는 이미 실패 기록·알림까지 남음)
            log.info("포인트 충전 대사 배치: 주문 {} 복구 시도 중단 — {}", order.getPtChgOrdNo(), e.getMessage());
            return Outcome.SKIPPED;
        } catch (RuntimeException e) {
            log.warn("포인트 충전 대사 배치: 주문 {} 복구 중 오류 — 다음 주기에 재시도", order.getPtChgOrdNo(), e);
            return Outcome.SKIPPED;
        }

        riskEventService.recordOnce(new RiskEventCommand(
                RISK_TYPE_PAYMENT,
                null,
                null,
                "confirm 콜백 유실 후 배치 자동복구: 충전 주문 " + order.getPtChgOrdNo()
                        + " (회원 " + order.getUsrSn() + ", " + order.getPtChgOrdAmt() + "P) — 관리자 확인 권장",
                "SYSTEM"));
        return Outcome.RECOVERED;
    }

    private enum Outcome { RECOVERED, EXPIRED, SKIPPED }
}
