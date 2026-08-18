package nct.ops.dashboard.controller;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import nct.global.exception.GlobalExceptionHandler;
import nct.ops.dashboard.service.AdminDashboardService;
import nct.ops.security.service.SensitiveDataMasker;

/** 담당자 7 · F-OPS-010: 운영 대시보드 라우트와 표준 오류 응답 계약을 검증합니다. */
class AdminDashboardControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminDashboardController(mock(AdminDashboardService.class)))
                .setControllerAdvice(new GlobalExceptionHandler(new SensitiveDataMasker()))
                .build();
    }

    /** 담당자 7 · ISSUE-T7-005: 읽기 전용 관리자 API의 잘못된 메서드는 표준 405로 끝납니다. */
    @Test
    void rejectsUnsupportedMethodWithStandardMethodNotAllowedResponse() throws Exception {
        mockMvc.perform(post("/api/admin/dashboard/summary"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.httpCode").value(405))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.path").value("/api/admin/dashboard/summary"));
    }
}
