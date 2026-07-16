package nct.ops.notice.controller;

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

import nct.ops.notice.dto.AdminNoticeOptionsResponse;
import nct.ops.notice.service.AdminNoticeService;

/** 관리자 공지 API가 화면 숨김이 아니라 서버 ROLE_ADMIN 검사로 보호되는지 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminNoticeSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminNoticeService adminNoticeService;

    @Test
    void rejectsAnonymousUser() throws Exception {
        mockMvc.perform(get("/api/admin/notices/options"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.httpCode").value(401));
    }

    @Test
    void rejectsAuthenticatedNonAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/notices/options")
                        .with(user("user@example.com").authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.httpCode").value(403));
    }

    @Test
    void allowsAdmin() throws Exception {
        when(adminNoticeService.getOptions()).thenReturn(AdminNoticeOptionsResponse.builder()
                .types(List.of())
                .statuses(List.of())
                .build());

        mockMvc.perform(get("/api/admin/notices/options")
                        .with(user("admin@example.com").authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }
}
