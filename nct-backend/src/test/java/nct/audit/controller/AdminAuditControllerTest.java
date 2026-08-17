package nct.audit.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import nct.audit.service.AuditLogService;
import nct.global.exception.GlobalExceptionHandler;
import nct.member.port.AdminMemberIdentityReader;
import nct.ops.security.service.SensitiveDataMasker;

/** 담당자 7 · F-OPS-015/016: 감사로그 조회 조건과 표준 오류 응답을 검증합니다. */
class AdminAuditControllerTest {

    private AuditLogService auditLogService;
    private AdminMemberIdentityReader memberIdentityReader;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        auditLogService = mock(AuditLogService.class);
        memberIdentityReader = mock(AdminMemberIdentityReader.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminAuditController(auditLogService, memberIdentityReader))
                .setControllerAdvice(new GlobalExceptionHandler(new SensitiveDataMasker()))
                .build();
    }

    @Test
    void returnsLogsForValidFilters() throws Exception {
        when(auditLogService.search(any(), anyString(), any(), any(), anyInt()))
                .thenReturn(List.of());
        when(memberIdentityReader.findByUserSns(any())).thenReturn(Map.of());

        mockMvc.perform(get("/api/admin/audit/logs")
                        .param("usrSn", "12")
                        .param("typeCd", "AUDC0001")
                        .param("from", "2026-08-16")
                        .param("to", "2026-08-17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        verify(auditLogService).search(
                12L,
                "AUDC0001",
                LocalDateTime.of(2026, 8, 16, 0, 0),
                LocalDateTime.of(2026, 8, 17, 23, 59, 59, 999_999_999),
                100);
    }

    @Test
    void rejectsNonPositiveActorWithoutReadingLogs() throws Exception {
        assertInvalidInput("usrSn", "0");
        assertInvalidInput("usrSn", "-1");

        verifyNoInteractions(auditLogService, memberIdentityReader);
    }

    @Test
    void rejectsReversedDateRangeWithoutReadingLogs() throws Exception {
        mockMvc.perform(get("/api/admin/audit/logs")
                        .param("from", "2026-08-18")
                        .param("to", "2026-08-17"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpCode").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verifyNoInteractions(auditLogService, memberIdentityReader);
    }

    @Test
    void rejectsUnknownAuditTypeWithoutReadingLogs() throws Exception {
        assertInvalidInput("typeCd", "AUDC9999");

        verifyNoInteractions(auditLogService, memberIdentityReader);
    }

    private void assertInvalidInput(String name, String value) throws Exception {
        mockMvc.perform(get("/api/admin/audit/logs").param(name, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.httpCode").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.path").value("/api/admin/audit/logs"));
    }
}
