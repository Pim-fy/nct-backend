package nct.ops.notice.adapter;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import nct.audit.domain.AuditLogType;
import nct.audit.service.AuditLogService;
import nct.common.domain.RefType;
import nct.ops.notice.port.NoticeChangeHistoryCommand;
import nct.ops.security.service.SensitiveDataMasker;

class AuditNoticeChangeHistoryAdapterTest {

    @Test
    void recordsNoticeChangeToAuditLog() {
        AuditLogService auditLogService = mock(AuditLogService.class);
        AuditNoticeChangeHistoryAdapter adapter = new AuditNoticeChangeHistoryAdapter(
                auditLogService, new SensitiveDataMasker());

        adapter.record(NoticeChangeHistoryCommand.builder()
                .action("HIDE")
                .actorUserId(7L)
                .noticeId(11L)
                .reason("점검 공지 숨김")
                .beforeSummary("status=published")
                .afterSummary("status=hidden")
                .build());

        verify(auditLogService).record(
                eq(7L),
                eq(AuditLogType.STATUS_CHANGE),
                eq(RefType.NOTICE),
                eq(11L),
                contains("점검 공지 숨김"),
                isNull());
    }
}
