package nct.ops.servicequery.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/** 담당자 7: 관리자 서비스 요청 컨트롤러가 ROLE_ADMIN 권한을 직접 선언하는지 검증한다. */
class AdminServiceRequestSecurityTest {

    @Test
    void requiresAdminAuthorityAtControllerBoundary() {
        PreAuthorize policy = AdminServiceRequestQueryController.class.getAnnotation(PreAuthorize.class);

        assertThat(policy).isNotNull();
        assertThat(policy.value()).isEqualTo("hasAuthority('ROLE_ADMIN')");
    }
}
