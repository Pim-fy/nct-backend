package nct.ops.reference.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nct.ops.reference.port.CategoryChangeHistoryCommand;
import nct.ops.reference.port.CategoryChangeHistoryPort;
import nct.ops.security.service.SensitiveDataMasker;

/** 담당자 6의 AUDIT_LOG 어댑터 전까지 사용하는 임시 구현이며 개인정보를 마스킹한다. */
@Slf4j
@RequiredArgsConstructor
public class LoggingCategoryChangeHistoryAdapter implements CategoryChangeHistoryPort {
    private final SensitiveDataMasker masker;

    @Override
    public void record(CategoryChangeHistoryCommand command) {
        log.info("CATEGORY_CHANGE action={} actorUserId={} categorySn={} reason={} before={} after={}",
                command.action(), command.actorUserId(), command.categorySn(), safe(command.reason()),
                safe(command.beforeSummary()), safe(command.afterSummary()));
    }

    private String safe(String value) {
        return masker.maskText(value == null ? null : value.replaceAll("[\\r\\n\\t]+", " "));
    }
}
