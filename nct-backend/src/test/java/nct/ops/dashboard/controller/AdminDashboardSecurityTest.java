package nct.ops.dashboard.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import nct.ops.dashboard.dto.AdminDashboardSummaryResponse;
import nct.ops.dashboard.service.AdminDashboardService;

/** 담당자 7 · F-OPS-010: 운영 대시보드 API의 서버 관리자 권한을 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminDashboardSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminDashboardService adminDashboardService;

    @Test
    void rejectsAnonymousUser() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.httpCode").value(401));
    }

    @Test
    void rejectsAuthenticatedNonAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/summary")
                        .with(user("user@example.com").authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.httpCode").value(403));
    }

    @Test
    void allowsAdmin() throws Exception {
        AdminDashboardSummaryResponse response = new AdminDashboardSummaryResponse();
        response.setActiveUserCount(12L);
        response.setPendingExchangeCount(2L);
        when(adminDashboardService.getSummary()).thenReturn(response);

        mockMvc.perform(get("/api/admin/dashboard/summary")
                        .with(user("admin@example.com").authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.activeUserCount").value(12))
                .andExpect(jsonPath("$.data.pendingExchangeCount").value(2));
    }
}
