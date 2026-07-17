package nct.ops.notice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import nct.ops.notice.adapter.LoggingNoticeChangeHistoryAdapter;
import nct.ops.notice.port.NoticeChangeHistoryPort;
import nct.ops.security.service.SensitiveDataMasker;

/**
 * 공지 변경 이력의 기본 연결 지점을 제공한다.
 * 담당자 6의 공용 감사 저장 어댑터가 등록되면 이 임시 로그 어댑터는 자동으로 빠진다.
 */
@Configuration
public class NoticeChangeHistoryConfiguration {

    @Bean
    @ConditionalOnMissingBean(NoticeChangeHistoryPort.class)
    NoticeChangeHistoryPort noticeChangeHistoryPort(SensitiveDataMasker sensitiveDataMasker) {
        return new LoggingNoticeChangeHistoryAdapter(sensitiveDataMasker);
    }
}
