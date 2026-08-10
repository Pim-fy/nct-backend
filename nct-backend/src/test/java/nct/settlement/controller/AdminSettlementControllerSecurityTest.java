package nct.settlement.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminSettlementControllerSecurityTest {

    @Test
    void controllerRequiresAdminAuthority() {
        PreAuthorize policy = AdminSettlementController.class.getAnnotation(PreAuthorize.class);

        assertThat(policy).isNotNull();
        assertThat(policy.value()).isEqualTo("hasAuthority('ROLE_ADMIN')");
    }
}
