package nct.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 담당자 7 · F-COM-009: 제공자 통합 평점 캐시 SQL의 권한 경계를 고정한다. */
class ReviewRatingCacheMapperContractTest {

    @Test
    void reviewRatingSummaryUsesAllActiveReviewsReceivedByTheMember() throws IOException {
        String mapper = resource("mapper/review/ReviewMapper.xml");

        assertThat(mapper)
                .contains("<select id=\"selectReviewRatingSummary\"")
                .contains("R.RVW_USE_YN = 'Y'")
                .contains("R.REVWD_USR_SN = #{usrSn}")
                .doesNotContain("selectServiceReviewRatingSummary");
    }

    @Test
    void mutationTargetLookupRequiresActiveReviewOwnedByCurrentWriter() throws IOException {
        String mapper = resource("mapper/review/ReviewMapper.xml");

        assertThat(mapper)
                .contains("<select id=\"selectOwnedActiveReviewRatingTarget\"")
                .contains("R.RVW_SN = #{rvwSn}")
                .contains("R.REVWR_USR_SN = #{usrSn}")
                .contains("R.RVW_USE_YN = 'Y'")
                .contains("R.REVWD_USR_SN AS receiverUserSn");
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
