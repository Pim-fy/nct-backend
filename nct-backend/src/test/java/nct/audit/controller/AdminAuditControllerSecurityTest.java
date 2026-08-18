package nct.audit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import nct.audit.service.AuditLogService;
import nct.member.port.AdminMemberIdentityReader;

/** 담당자 7 · F-OPS-014/016: 감사 API의 실제 관리자 권한 차단을 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuditControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogService auditLogService;

    @MockitoBean
    private AdminMemberIdentityReader memberIdentityReader;

    @Test
    void declaresExactAdminAuthority() {
        PreAuthorize annotation = AdminAuditController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAuthority('ROLE_ADMIN')");
    }

    @Test
    void rejectsAuthenticatedNonAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/audit/logs")
                        .with(user("user@example.com").authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.httpCode").value(403));
    }

    @Test
    void rejectsAnonymousUser() throws Exception {
        mockMvc.perform(get("/api/admin/audit/logs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.httpCode").value(401));
    }

    @Test
    void allowsAdmin() throws Exception {
        when(auditLogService.search(null, null, null, null, 100)).thenReturn(List.of());
        when(memberIdentityReader.findByUserSns(List.of())).thenReturn(Map.of());

        mockMvc.perform(get("/api/admin/audit/logs")
                        .with(user("admin@example.com").authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }
}
