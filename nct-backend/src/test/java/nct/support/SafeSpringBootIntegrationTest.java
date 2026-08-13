package nct.support;

import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import nct.global.idempotency.IdempotencyCleanupScheduler;
import nct.ops.sanction.scheduler.ReportSanctionExpiryScheduler;

/**
 * 담당자 7 · F-OPS-018: Spring 통합 테스트가 기동될 때 별도 스케줄러가
 * 테스트 트랜잭션 밖의 공용 DB를 변경하지 못하도록 막는 공통 기반입니다.
 */
@TestPropertySource(properties = {
        "auction.finalization.scheduler-enabled=false",
        "product.cancelled-auto-delete.enabled=false",
        "service-request.auto-close.scheduler-enabled=false",
        "service-request.closed-auto-delete.enabled=false",
        "trade.auto-completion.enabled=false",
        "point.reconciliation.scheduler.enabled=false"
})
public abstract class SafeSpringBootIntegrationTest {

    @MockitoBean
    private IdempotencyCleanupScheduler idempotencyCleanupScheduler;

    @MockitoBean
    private ReportSanctionExpiryScheduler reportSanctionExpiryScheduler;
}
