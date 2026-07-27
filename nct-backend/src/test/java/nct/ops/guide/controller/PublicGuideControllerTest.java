package nct.ops.guide.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PathVariable;

import nct.global.exception.GlobalExceptionHandler;
import nct.ops.guide.dto.PublicGuideListItemResponse;
import nct.ops.guide.service.PublicGuideService;
import nct.ops.security.service.SensitiveDataMasker;

/** 담당자 7 · F-COM-014 공개 이용가이드 컨트롤러 계약 검증입니다. */
class PublicGuideControllerTest {

    private PublicGuideService publicGuideService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        publicGuideService = mock(PublicGuideService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PublicGuideController(publicGuideService))
                .setControllerAdvice(new GlobalExceptionHandler(new SensitiveDataMasker()))
                .build();
    }

    @Test
    void declaresExplicitPathVariableNameForStsCompilerCompatibility() throws Exception {
        Method detailMethod = PublicGuideController.class.getDeclaredMethod(
                "getGuide", String.class);

        assertThat(detailMethod.getParameters()[0].getAnnotation(PathVariable.class).name())
                .isEqualTo("guideId");
    }

    @Test
    void returnsGuideList() throws Exception {
        org.mockito.Mockito.when(publicGuideService.getGuides()).thenReturn(List.of(
                new PublicGuideListItemResponse("product-register", "상품 등록", "summary",
                        "/guide/product-register", 10)));

        mockMvc.perform(get("/api/guides"))
                .andExpect(status().isOk());

        verify(publicGuideService).getGuides();
    }

    @Test
    void forwardsGuideIdToService() throws Exception {
        mockMvc.perform(get("/api/guides/bid"))
                .andExpect(status().isOk());

        verify(publicGuideService).getGuide("bid");
    }
}
