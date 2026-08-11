package nct.ops.operation.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/** 담당자 7 · F-OPS-005: 분쟁 증빙 원문 API가 관리자 전용인지 검증합니다. */
class AdminDisputeEvidenceFileControllerTest {

    @Test
    void declaresExactAdminAuthority() {
        PreAuthorize annotation = AdminDisputeEvidenceFileController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAuthority('ROLE_ADMIN')");
    }
}
