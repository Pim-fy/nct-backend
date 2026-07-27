package nct.ops.guide.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import nct.ops.guide.dto.PublicGuideListItemResponse;
import nct.ops.guide.service.PublicGuideService;

/** 담당자 7 · F-COM-014 이용가이드 조회만 방문자에게 공개되는지 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
class PublicGuideSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicGuideService publicGuideService;

    @Test
    void allowsAnonymousGuideList() throws Exception {
        when(publicGuideService.getGuides()).thenReturn(List.of(
                new PublicGuideListItemResponse("product-register", "상품 등록", "summary",
                        "/guide/product-register", 10)));

        mockMvc.perform(get("/api/guides"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void doesNotOpenGuideWriteRequests() throws Exception {
        mockMvc.perform(post("/api/guides").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.httpCode").value(401));
    }
}
