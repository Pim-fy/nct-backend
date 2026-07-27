package nct.point;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import nct.ops.risk.service.RiskEventCommand;
import nct.ops.risk.service.RiskEventResult;
import nct.ops.risk.service.RiskEventService;
import nct.point.mapper.PointMapper;
import nct.point.scheduler.PointLedgerReconciliationScheduler;

/**
 * Claude Code 작성 (BJN, 2026-07-21)
 *
 * [테스트 - F-PT-06/QSC-PT-08 포인트 대사 배치]
 *
 * PointMapper를 가짜로 바꿔 "항등식 성립/불일치" 두 상황을 결정론적으로 재현한다 — 실제
 * POINT_LEDGER 데이터(공유 DB, 다른 세션의 활동으로 계속 변함)에 기대지 않는다.
 * RiskEventService도 가짜로 바꾼다 — 실제로 호출하게 두면 DB에 영구히 남는다(2026-07-21
 * 확인 — @Transactional 테스트 롤백이 이 경로엔 왜인지 적용되지 않아 공유 DB에 위험 이벤트
 * 잔여물이 실제로 남았던 사고). 다른 기존 테스트들(SensitiveDataInspectionServiceTest 등)도
 * 전부 RiskEventService를 가짜로 바꿔 쓰는 게 이 코드베이스의 관례라 그대로 따른다 — 호출
 * 여부만 verify로 확인하면 충분하다.
 *
 * point.reconciliation.scheduler.enabled를 이 클래스에서만 true로 되돌린다 — 다른 모든
 * 테스트에서는 기본 꺼짐이라 스케줄 자동실행으로 실DB가 건드려지지 않는다.
 */
@SpringBootTest
@TestPropertySource(properties = "point.reconciliation.scheduler.enabled=true")
@Transactional
class PointLedgerReconciliationSchedulerTest {

    @Autowired PointLedgerReconciliationScheduler scheduler;

    @MockitoBean PointMapper pointMapper;
    @MockitoBean RiskEventService riskEventService;

    @Test
    @DisplayName("항등식이 성립하면(전체 원장 합계 = 충전+환전+보정 합계) 위험 이벤트를 남기지 않는다")
    void noRiskEventWhenIdentityHolds() {
        when(pointMapper.selectTotalLedgerSum()).thenReturn(1_000_000L);
        when(pointMapper.selectBoundaryCrossingSum()).thenReturn(1_000_000L);

        scheduler.checkLedgerIdentity();

        verify(riskEventService, never()).recordOnce(any(RiskEventCommand.class));
    }

    @Test
    @DisplayName("항등식이 깨지면 결제위험(RSKC0003) 이벤트를 기록해 관리자에게 남긴다")
    void recordsRiskEventWhenIdentityBreaks() {
        when(pointMapper.selectTotalLedgerSum()).thenReturn(1_000_000L);
        when(pointMapper.selectBoundaryCrossingSum()).thenReturn(950_000L);
        when(riskEventService.recordOnce(any(RiskEventCommand.class)))
                .thenReturn(new RiskEventResult(1L, true));

        scheduler.checkLedgerIdentity();

        verify(riskEventService).recordOnce(any(RiskEventCommand.class));
    }
}
