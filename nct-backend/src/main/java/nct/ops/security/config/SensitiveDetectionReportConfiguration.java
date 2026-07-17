package nct.ops.security.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import nct.ops.security.adapter.DeferredSensitiveDetectionReportAdapter;
import nct.ops.security.port.SensitiveDetectionReportPort;

/**
 * F-OPS-013 신고 연결 플러그를 선택하는 설정이다.
 *
 * <p>담당자 5의 실제 {@link SensitiveDetectionReportPort}가 아직 없으면 DEFERRED를
 * 반환하는 임시 구현을 등록한다. 나중에 실제 구현 Bean이 추가되면 이 임시 Bean은
 * 등록되지 않아 호출 코드를 수정하지 않고도 교체할 수 있다.</p>
 */
@Configuration(proxyBeanMethods = false)
public class SensitiveDetectionReportConfiguration {

    @Bean
    @ConditionalOnMissingBean(SensitiveDetectionReportPort.class)
    SensitiveDetectionReportPort deferredSensitiveDetectionReportPort() {
        return new DeferredSensitiveDetectionReportAdapter();
    }
}
