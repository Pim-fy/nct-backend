package nct.ops.notice.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import nct.global.exception.ErrorCode;
import nct.global.exception.GlobalExceptionHandler;
import nct.ops.notice.service.PublicNoticeService;
import nct.ops.security.service.SensitiveDataMasker;

/** F-COM-013 공개 공지 요청의 컨트롤러 입력 경계를 확인한다. */
class PublicNoticeControllerTest {

    private PublicNoticeService publicNoticeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        publicNoticeService = mock(PublicNoticeService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PublicNoticeController(publicNoticeService))
                .setControllerAdvice(new GlobalExceptionHandler(new SensitiveDataMasker()))
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/notices?page=not-a-number",
            "/api/notices?size=not-a-number",
            "/api/notices/not-a-number"
    })
    void returnsBadRequestForNonNumericInputs(String requestUri) throws Exception {
        mockMvc.perform(get(requestUri))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpCode").value(400))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_TYPE_VALUE.message()));

        verifyNoInteractions(publicNoticeService);
    }
}
