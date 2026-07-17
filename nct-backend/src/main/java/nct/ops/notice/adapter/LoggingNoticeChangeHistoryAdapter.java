package nct.ops.notice.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nct.ops.notice.port.NoticeChangeHistoryCommand;
import nct.ops.notice.port.NoticeChangeHistoryPort;
import nct.ops.security.service.SensitiveDataMasker;

/**
 * AUDIT_LOG 제공 계약이 오기 전 사용하는 임시 어댑터다.
 * 개인정보가 섞일 수 있는 사유는 마스킹하고, 공지 본문은 기록하지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
public class LoggingNoticeChangeHistoryAdapter implements NoticeChangeHistoryPort {

    private final SensitiveDataMasker sensitiveDataMasker;

    @Override
    public void record(NoticeChangeHistoryCommand command) {
        log.info("NOTICE_CHANGE action={} actorUserId={} noticeId={} reason={} before={} after={}",
                command.getAction(),
                command.getActorUserId(),
                command.getNoticeId(),
                sanitizeForSingleLineLog(sensitiveDataMasker.maskText(command.getReason())),
                command.getBeforeSummary(),
                command.getAfterSummary());
    }

    private String sanitizeForSingleLineLog(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n\\t]+", " ");
    }
}
