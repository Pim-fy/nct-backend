package nct.ops.reference.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import nct.ops.reference.adapter.LoggingCategoryChangeHistoryAdapter;
import nct.ops.reference.port.CategoryChangeHistoryPort;
import nct.ops.security.service.SensitiveDataMasker;

/** 담당자 6의 공용 감사 구현이 등록되면 임시 로그 어댑터가 자동으로 빠지는 연결 설정이다. */
@Configuration
public class CategoryChangeHistoryConfiguration {
    @Bean
    @ConditionalOnMissingBean(CategoryChangeHistoryPort.class)
    CategoryChangeHistoryPort categoryChangeHistoryPort(SensitiveDataMasker masker) {
        return new LoggingCategoryChangeHistoryAdapter(masker);
    }
}
