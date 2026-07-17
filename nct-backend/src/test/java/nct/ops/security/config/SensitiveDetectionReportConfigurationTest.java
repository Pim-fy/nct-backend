package nct.ops.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import nct.ops.security.adapter.DeferredSensitiveDetectionReportAdapter;
import nct.ops.security.port.SensitiveDetectionReportPort;
import nct.ops.security.port.SensitiveDetectionReportResult;

/**
 * 담당자 5의 실제 신고 플러그가 들어올 때 임시 플러그와 충돌하지 않는지 확인한다.
 */
class SensitiveDetectionReportConfigurationTest {

    @Test
    void registersDeferredAdapterWhenActualReportPortIsMissing() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(SensitiveDetectionReportConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(SensitiveDetectionReportPort.class))
                    .hasSize(1)
                    .allSatisfy((name, port) ->
                            assertThat(port).isInstanceOf(DeferredSensitiveDetectionReportAdapter.class));
        }
    }

    @Test
    void usesOnlyActualReportPortWhenAssigneeFiveProvidesOne() {
        SensitiveDetectionReportPort actualPort = command ->
                new SensitiveDetectionReportResult(
                        SensitiveDetectionReportResult.Status.REUSED, 100L);

        try (var context = new AnnotationConfigApplicationContext()) {
            // 실제 Bean 정의와 조건부 설정을 함께 등록해 애플리케이션 병합 상황을 재현한다.
            context.registerBean("actualSensitiveDetectionReportPort",
                    SensitiveDetectionReportPort.class, () -> actualPort);
            context.register(SensitiveDetectionReportConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(SensitiveDetectionReportPort.class))
                    .containsOnlyKeys("actualSensitiveDetectionReportPort")
                    .containsValue(actualPort);
        }
    }
}
