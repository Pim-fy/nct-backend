package nct.ops.reference.controller;

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

import nct.ops.reference.service.AdminCategoryService;
import nct.ops.reference.service.ReferenceDataService;

/** 담당자 7 · F-COM-003: 공개 조회와 관리자 변경 권한 경계를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class CategorySecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReferenceDataService referenceDataService;

    @MockitoBean
    private AdminCategoryService adminCategoryService;

    @Test
    void allowsAnonymousActiveCategoryLookup() throws Exception {
        when(referenceDataService.getActiveCategories("CATC0001")).thenReturn(List.of());
        mockMvc.perform(get("/api/categories").param("domainCd", "CATC0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void rejectsNonAdminCategoryManagement() throws Exception {
        mockMvc.perform(get("/api/admin/categories/CATC0001")
                        .with(user("user").authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAdminCategoryManagement() throws Exception {
        when(adminCategoryService.getCategories("CATC0001")).thenReturn(List.of());
        mockMvc.perform(get("/api/admin/categories/CATC0001")
                        .with(user("admin").authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
