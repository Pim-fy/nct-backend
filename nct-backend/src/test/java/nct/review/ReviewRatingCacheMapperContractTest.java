package nct.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 담당자 7 · REQ-COM-012: 제공자 서비스 평점 캐시 SQL의 도메인 경계를 고정한다. */
class ReviewRatingCacheMapperContractTest {

    @Test
    void reviewRatingSummaryUsesOnlyActiveServiceReviewsReceivedByTheMember() throws IOException {
        String mapper = resource("mapper/review/ReviewMapper.xml");

        assertThat(mapper)
                .contains("<select id=\"selectServiceReviewRatingSummary\"")
                .contains("R.RVW_USE_YN = 'Y'")
                .contains("R.REVWD_USR_SN = #{usrSn}")
                .contains("R.RVW_DOMAIN_CD = 'RVWC0002'");
    }

    @Test
    void mutationTargetLookupRequiresActiveReviewOwnedByCurrentWriter() throws IOException {
        String mapper = resource("mapper/review/ReviewMapper.xml");

        assertThat(mapper)
                .contains("<select id=\"selectOwnedActiveReviewRatingTarget\"")
                .contains("R.RVW_SN = #{rvwSn}")
                .contains("R.REVWR_USR_SN = #{usrSn}")
                .contains("R.RVW_USE_YN = 'Y'")
                .contains("R.REVWD_USR_SN AS receiverUserSn")
                .contains("R.RVW_DOMAIN_CD AS reviewDomainCode");
    }

    @Test
    void publicTrustScoreIsRestrictedToTheRequestedReviewDomain() throws IOException {
        String mapper = resource("mapper/review/ReviewMapper.xml");
        String trustScoreQuery = mapper.substring(
                mapper.indexOf("<select id=\"selectTrustScore\""),
                mapper.indexOf("</select>", mapper.indexOf("<select id=\"selectTrustScore\"")));

        assertThat(trustScoreQuery)
                .contains("R.RVW_USE_YN = 'Y'")
                .contains("R.RVW_DOMAIN_CD = #{reviewDomainCode}");

        assertThat(mapper)
                .doesNotContain("selectReviewRatingSummary");
    }

    @Test
    void receivedReviewListUsesTheReviewDomainAsItsDealTypeSource() throws IOException {
        String mapper = resource("mapper/review/ReviewMapper.xml");
        String listQuery = mapper.substring(
                mapper.indexOf("<select id=\"selectReviewsByReceiver\""),
                mapper.indexOf("</select>", mapper.indexOf("<select id=\"selectReviewsByReceiver\"")));
        String countQuery = mapper.substring(
                mapper.indexOf("<select id=\"countReviewsByReceiver\""),
                mapper.indexOf("</select>", mapper.indexOf("<select id=\"countReviewsByReceiver\"")));

        assertThat(listQuery)
                .contains("R.RVW_DOMAIN_CD = 'RVWC0001'")
                .contains("R.RVW_DOMAIN_CD = 'RVWC0002'");
        assertThat(countQuery)
                .contains("R.RVW_DOMAIN_CD = 'RVWC0001'")
                .contains("R.RVW_DOMAIN_CD = 'RVWC0002'");
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
