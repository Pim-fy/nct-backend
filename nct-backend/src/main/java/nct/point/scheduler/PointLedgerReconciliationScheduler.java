package nct.point.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nct.ops.risk.service.RiskEventCommand;
import nct.ops.risk.service.RiskEventService;
import nct.point.mapper.PointMapper;

/**
 * Claude Code 작성 (BJN, 2026-07-21)
 *
 * [포인트 대사 배치] (F-PT-06 / QSC-PT-08)
 *
 * 원장은 회원별 카테고리 이동을 단일행으로 기록하는 구조다. 홀딩(HOLD)·반환(RELEASE)·
 * 전환(CONVERT)은 PointService가 항상 같은 트랜잭션 안에서 −/+ 두 행을 함께 기록하므로
 * 어느 시점에 봐도 합이 0이다 — 정상이라면 이 셋의 합계가 항상 0이라는 게 진짜 검증하려는
 * 불변식이다. 나머지(충전·환전출금·환전복원·보정·보관금전환·정산·환불)는 전부 SUM에
 * 그대로 포함시킨다(아래 selectBoundaryCrossingSum 쿼리 참고). 특히 보관금전환(ESCROW)은
 * 낙찰 시점에 단식 1행(−)만 기록되고 짝(정산/환불)은 거래가 끝나야 나중에 따로 기록되므로
 * (2026-08-05, 조우진 실DB 진단으로 발견 — 예전엔 이 셋을 "내부 이동이라 0"으로 보고 계산식에서
 * 뺐다가, 진행 중 거래가 있을 때마다 그 보관금 잔액만큼 거짓 불일치가 났다), 이걸 좌변·우변에
 * 동일하게 포함시켜야 진행 중 거래 유무와 무관하게 항등식이 성립한다. 따라서 다음 항등식이
 * 항상 성립해야 한다:
 *
 *   전체 원장 합계(SUM(PT_LDG_AMT), 전 회원) == (전체 합계 − 홀딩 합계 − 반환 합계 − 전환 합계)
 *
 * 이 항등식이 깨지면 홀딩/반환/전환 중 어딘가에 짝이 안 맞는 카테고리 이동(버그)이나 수동 DB
 * 조작이 있었다는 뜻이다. 하루 한 번이면 충분한 점검이라 매일 정상 통과하는 게 정상 상태 —
 * 일치할 때는 조용히 넘어가고, 불일치할 때만 관리자에게 위험 이벤트로 알린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
// PointChargeReconciliationScheduler와 같은 스위치를 공유한다 — 이유는 그쪽 클래스 주석 참고.
@ConditionalOnProperty(prefix = "point.reconciliation.scheduler", name = "enabled", havingValue = "true", matchIfMissing = false)
public class PointLedgerReconciliationScheduler {

    /** RSKG01(위험 이벤트 유형) 중 '결제위험' — 정본 기초데이터에 이미 있는 코드, 신규 등록 불필요 */
    private static final String RISK_TYPE_PAYMENT = "RSKC0003";

    private static final long DAILY_MS = 24 * 60 * 60 * 1000L;

    private final PointMapper pointMapper;
    private final RiskEventService riskEventService;

    @Scheduled(fixedDelay = DAILY_MS)
    public void reconcile() {
        checkLedgerIdentity();
    }

    /**
     * 실제 로직은 별도 public 메서드로 분리 — 테스트에서 스케줄 대기 없이 직접 호출 가능하도록.
     * 두 합계를 한 트랜잭션 안에서 읽어야 그 사이 다른 요청의 쓰기가 끼어들어 생기는
     * 거짓 불일치(false positive)를 피할 수 있다. readOnly로 주지 않는 이유: 불일치 발견 시
     * 같은 트랜잭션 안에서 RiskEventService.recordOnce(쓰기)까지 이어지기 때문 — readOnly
     * 힌트가 커넥션까지 전파되면(MySQL Connector/J readOnlyPropagatesToServer) 그 INSERT가
     * 거부된다.
     */
    @Transactional
    public void checkLedgerIdentity() {
        long totalSum = pointMapper.selectTotalLedgerSum();
        long boundarySum = pointMapper.selectBoundaryCrossingSum();

        if (totalSum == boundarySum) {
            log.debug("포인트 대사 배치: 항등식 정상 (전체 원장 합계 {}P = 대사 기준 합계)", totalSum);
            return;
        }

        long diff = totalSum - boundarySum;
        log.warn("포인트 대사 배치: 항등식 불일치 — 전체 원장 합계 {}P, 대사 기준 합계 {}P, 차이 {}P",
                totalSum, boundarySum, diff);
        riskEventService.recordOnce(new RiskEventCommand(
                RISK_TYPE_PAYMENT,
                null,
                null,
                "포인트 원장 대사 불일치: 전체 합계 " + totalSum + "P, 대사 기준 합계 " + boundarySum
                        + "P, 차이 " + diff + "P — 원장 무결성 점검 필요",
                "SYSTEM"));
    }
}
