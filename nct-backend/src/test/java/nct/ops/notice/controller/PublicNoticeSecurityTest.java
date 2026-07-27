package nct.ops.notice.controller;

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

import nct.ops.notice.dto.PublicNoticePageResponse;
import nct.ops.notice.service.PublicNoticeService;

/** 담당자 7 · F-COM-013: 공지 조회만 방문자에게 공개되는 보안 경계를 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class PublicNoticeSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicNoticeService publicNoticeService;

    @Test
    void allowsAnonymousPublicNoticeList() throws Exception {
        when(publicNoticeService.getPublicNotices(null, null, 1, 10)).thenReturn(PublicNoticePageResponse.builder()
                .items(List.of()).page(1).size(10).totalItems(0).totalPages(0).build());

        mockMvc.perform(get("/api/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void doesNotOpenNoticeWriteRequests() throws Exception {
        mockMvc.perform(post("/api/notices").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.httpCode").value(401));
    }
}
