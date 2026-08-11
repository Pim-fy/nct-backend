package nct.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 담당자 7 · F-COM-009: 제공자 리뷰 별점 캐시 SQL의 도메인·권한 경계를 고정한다. */
class ReviewRatingCacheMapperContractTest {

    @Test
    void serviceRatingSummaryUsesOnlyActiveReviewsReceivedAsProvider() throws IOException {
        String mapper = resource("mapper/review/ReviewMapper.xml");

        assertThat(mapper)
                .contains("<select id=\"selectServiceReviewRatingSummary\"")
                .contains("R.RVW_DOMAIN_CD = 'RVWC0002'")
                .contains("R.RVW_USE_YN = 'Y'")
                .contains("T.TRD_TYPE_CD = 'TRDC0002'")
                .contains("R.REVWR_USR_SN = T.REQ_USR_SN")
                .contains("T.PRV_USR_SN = R.REVWD_USR_SN");
    }

    @Test
    void mutationTargetLookupRequiresActiveReviewOwnedByCurrentWriter() throws IOException {
        String mapper = resource("mapper/review/ReviewMapper.xml");

        assertThat(mapper)
                .contains("<select id=\"selectOwnedActiveReviewRatingTarget\"")
                .contains("R.RVW_SN = #{rvwSn}")
                .contains("R.REVWR_USR_SN = #{usrSn}")
                .contains("R.RVW_USE_YN = 'Y'")
                .contains("T.PRV_USR_SN = R.REVWD_USR_SN")
                .contains("AS receivedAsServiceProvider");
    }

    @Test
    void writableServiceReviewMarksOnlyRequesterToProviderDirectionAsCacheEligible() throws IOException {
        String mapper = resource("mapper/review/ReviewMapper.xml");

        assertThat(mapper)
                .contains("T.REQ_USR_SN = #{usrSn}")
                .contains("AS counterpartServiceProvider");
    }

    @Test
    void providerProfileCacheUpdateWritesOnlyReviewSummaryColumns() throws IOException {
        String mapper = resource("mapper/provider/ProviderProfileMapper.xml");

        assertThat(mapper)
                .contains("<update id=\"updateReviewRating\"")
                .contains("PRV_PRF_RVW_AVG_SCR = #{averageScore}")
                .contains("PRV_PRF_RVW_CNT = #{reviewCount}")
                .contains("WHERE USR_SN = #{userSn}");
    }

    @Test
    void providerProfileRowIsLockedBeforeConcurrentCacheRefresh() throws IOException {
        String mapper = resource("mapper/provider/ProviderProfileMapper.xml");

        assertThat(mapper)
                .contains("<select id=\"lockReviewRatingByUserSn\"")
                .contains("FROM PROVIDER_PROFILE")
                .contains("WHERE USR_SN = #{userSn}")
                .contains("FOR UPDATE");
    }

    private String resource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
