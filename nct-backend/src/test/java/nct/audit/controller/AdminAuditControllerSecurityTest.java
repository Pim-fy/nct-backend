package nct.audit.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/** 담당자 7 · F-OPS-014: 민감정보 제한 조회 Controller가 정확한 관리자 권한을 요구하는지 검증합니다. */
class AdminAuditControllerSecurityTest {

    @Test
    void declaresExactAdminAuthority() {
        PreAuthorize annotation = AdminAuditController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAuthority('ROLE_ADMIN')");
    }
}
