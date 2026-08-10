package nct.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/** 쓰기 허용과 승인 식별값이 모두 있어야 실제 DB 통합 테스트를 엽니다. */
public class ApprovedDatabaseWriteCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (!"true".equalsIgnoreCase(ApprovedDatabaseWriteIntegrationTest.setting(
                "nct.test.db.allow-write", "NCT_TEST_DB_ALLOW_WRITE"))) {
            return ConditionEvaluationResult.disabled(
                    "NCT_TEST_DB_ALLOW_WRITE=true가 없어 DB 쓰기 테스트를 건너뜁니다.");
        }

        String approval = ApprovedDatabaseWriteIntegrationTest.setting(
                "nct.test.db.approval", "NCT_TEST_DB_APPROVAL");
        if (approval == null || approval.isBlank()) {
            return ConditionEvaluationResult.disabled(
                    "DB 쓰기 승인 식별값 NCT_TEST_DB_APPROVAL이 필요합니다.");
        }

        return ConditionEvaluationResult.enabled(
                "DB 쓰기 승인이 확인됐습니다: " + approval.trim());
    }
}
