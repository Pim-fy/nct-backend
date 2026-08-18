package nct.review;

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

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.PageResponse;
import nct.review.dto.TrustScoreResponse;
import nct.review.dto.UserReviewItem;
import nct.review.service.ReviewService;
import nct.support.SafeSpringBootIntegrationTest;

/** 담당자 7 · F-COM-009: 공개 도메인별 평점 조회와 인증 전용 리뷰 API의 경계를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewSecurityTest extends SafeSpringBootIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @Test
    void allowsAnonymousTrustScoreLookup() throws Exception {
        when(reviewService.getTrustScore(42L, "goods")).thenReturn(TrustScoreResponse.builder()
                .usrSn(42L)
                .totalScore(4.5)
                .totalCount(8)
                .hasReviews(true)
                .build());

        mockMvc.perform(get("/api/reviews/trust/42").queryParam("dealType", "goods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usrSn").value(42))
                .andExpect(jsonPath("$.data.totalScore").value(4.5))
                .andExpect(jsonPath("$.data.totalCount").value(8))
                .andExpect(jsonPath("$.data.goodsScore").doesNotExist())
                .andExpect(jsonPath("$.data.serviceScore").doesNotExist());
    }

    @Test
    void allowsAnonymousServiceTrustScoreLookup() throws Exception {
        when(reviewService.getTrustScore(42L, "service")).thenReturn(TrustScoreResponse.builder()
                .usrSn(42L)
                .totalScore(4.8)
                .totalCount(5)
                .hasReviews(true)
                .build());

        mockMvc.perform(get("/api/reviews/trust/42").queryParam("dealType", "service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usrSn").value(42))
                .andExpect(jsonPath("$.data.totalScore").value(4.8))
                .andExpect(jsonPath("$.data.totalCount").value(5));
    }

    @Test
    void rejectsTrustScoreLookupWithoutAReviewDomain() throws Exception {
        mockMvc.perform(get("/api/reviews/trust/42"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTrustScoreLookupWithAnUnsupportedReviewDomain() throws Exception {
        mockMvc.perform(get("/api/reviews/trust/42").queryParam("dealType", "all"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTrustScoreLookupWithABlankReviewDomain() throws Exception {
        mockMvc.perform(get("/api/reviews/trust/42").queryParam("dealType", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void allowsGoodsReviewListLookup() throws Exception {
        when(reviewService.getReviewsAboutUser(42L, "goods", null, 0, 10))
                .thenReturn(emptyReviewPage());

        mockMvc.perform(get("/api/reviews/user/42")
                        .queryParam("dealType", "goods")
                        .with(user("user@example.com").authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk());
    }

    @Test
    void allowsServiceReviewListLookup() throws Exception {
        when(reviewService.getReviewsAboutUser(42L, "service", null, 0, 10))
                .thenReturn(emptyReviewPage());

        mockMvc.perform(get("/api/reviews/user/42")
                        .queryParam("dealType", "service")
                        .with(user("user@example.com").authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk());
    }

    @Test
    void allowsReviewListLookupWithoutDealType() throws Exception {
        when(reviewService.getReviewsAboutUser(42L, null, null, 0, 10))
                .thenReturn(emptyReviewPage());

        mockMvc.perform(get("/api/reviews/user/42")
                        .with(user("user@example.com").authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsReviewListLookupWithUnsupportedDealType() throws Exception {
        when(reviewService.getReviewsAboutUser(42L, "invalid", null, 0, 10))
                .thenThrow(new CustomException(
                        ErrorCode.INVALID_INPUT_VALUE,
                        "리뷰 거래 유형은 goods 또는 service여야 합니다."));

        mockMvc.perform(get("/api/reviews/user/42")
                        .queryParam("dealType", "invalid")
                        .with(user("user@example.com").authorities(() -> "ROLE_USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.httpCode").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.path").value("/api/reviews/user/42"));
    }

    @Test
    void rejectsReviewListLookupWithBlankDealType() throws Exception {
        when(reviewService.getReviewsAboutUser(42L, " ", null, 0, 10))
                .thenThrow(new CustomException(
                        ErrorCode.INVALID_INPUT_VALUE,
                        "리뷰 거래 유형은 goods 또는 service여야 합니다."));

        mockMvc.perform(get("/api/reviews/user/42")
                        .queryParam("dealType", " ")
                        .with(user("user@example.com").authorities(() -> "ROLE_USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.httpCode").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.path").value("/api/reviews/user/42"));
    }

    @Test
    void passesSellerRoleToGoodsReviewListLookup() throws Exception {
        when(reviewService.getReviewsAboutUser(42L, "goods", "SELLER", 0, 10))
                .thenReturn(emptyReviewPage());

        mockMvc.perform(get("/api/reviews/user/42")
                        .queryParam("dealType", "goods")
                        .queryParam("role", "SELLER")
                        .with(user("user@example.com").authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk());
    }

    @Test
    void passesBuyerRoleToGoodsReviewListLookup() throws Exception {
        when(reviewService.getReviewsAboutUser(42L, "goods", "BUYER", 0, 10))
                .thenReturn(emptyReviewPage());

        mockMvc.perform(get("/api/reviews/user/42")
                        .queryParam("dealType", "goods")
                        .queryParam("role", "BUYER")
                        .with(user("user@example.com").authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk());
    }

    @Test
    void keepsUserReviewListAuthenticated() throws Exception {
        mockMvc.perform(get("/api/reviews/user/42"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.httpCode").value(401));
    }

    @Test
    void keepsMyReviewsAuthenticated() throws Exception {
        mockMvc.perform(get("/api/reviews/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.httpCode").value(401));
    }

    private PageResponse<UserReviewItem> emptyReviewPage() {
        return PageResponse.<UserReviewItem>builder()
                .content(java.util.List.of())
                .totalCount(0)
                .page(0)
                .size(10)
                .hasNext(false)
                .build();
    }
}
