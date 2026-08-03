package nct.review;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import nct.review.dto.TrustScoreResponse;
import nct.review.service.ReviewService;

/** 담당자 7 · F-COM-009~010: 공개 신뢰도 조회와 인증 전용 리뷰 API의 경계를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @Test
    void allowsAnonymousTrustScoreLookup() throws Exception {
        when(reviewService.getTrustScore(42L)).thenReturn(TrustScoreResponse.builder()
                .usrSn(42L)
                .totalScore(4.5)
                .totalCount(8)
                .goodsScore(4.7)
                .goodsCount(5)
                .serviceScore(4.2)
                .serviceCount(3)
                .hasReviews(true)
                .build());

        mockMvc.perform(get("/api/reviews/trust/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usrSn").value(42))
                .andExpect(jsonPath("$.data.totalScore").value(4.5))
                .andExpect(jsonPath("$.data.totalCount").value(8));
    }

    @Test
    void keepsMyReviewsAuthenticated() throws Exception {
        mockMvc.perform(get("/api/reviews/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.httpCode").value(401));
    }
}
