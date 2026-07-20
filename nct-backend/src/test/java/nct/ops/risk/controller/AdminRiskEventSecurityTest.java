package nct.ops.risk.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import nct.ops.risk.service.AdminRiskEventService;

/** 담당자 7 · F-OPS-011: 리스크 API도 서버에서 관리자 권한을 막는지 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminRiskEventSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminRiskEventService adminRiskEventService;

    @Test
    void rejectsAnonymousUser() throws Exception {
        mockMvc.perform(get("/api/admin/risk-events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.httpCode").value(401));
    }

    @Test
    void rejectsAuthenticatedNonAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/risk-events")
                        .with(user("user@example.com").authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.httpCode").value(403));
    }

    @Test
    void allowsAdmin() throws Exception {
        when(adminRiskEventService.getTypeSummary(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/risk-events/summary")
                        .with(user("admin@example.com").authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }
}
