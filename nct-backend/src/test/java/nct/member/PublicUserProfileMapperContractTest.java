package nct.member;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** 담당자 7 통합 연결 · F-COM-008~009: 공개 조회 SQL의 필드·사용·탈퇴 제외 경계를 검증한다. */
class PublicUserProfileMapperContractTest {

    @Test
    void 공개_프로필_SQL은_허용된_필드만_선택하고_미사용과_탈퇴를_제외한다() throws IOException {
        String mapper = new String(
                getClass().getResourceAsStream("/mapper/member/MemberMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        int start = mapper.indexOf("<select id=\"findPublicProfileById\"");
        int end = mapper.indexOf("</select>", start);

        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        assertThat(mapper.substring(start, end))
                .contains("SELECT USR_SN AS userSn, USR_NM AS displayName, USR_PRFL_FL_SN AS profileFileSn")
                .contains("WHERE USR_SN = #{usrSn} AND USR_USE_YN = 'Y' AND USR_STATUS_CD &lt;&gt; 'USRC0003'")
                .doesNotContain("USR_LOGIN_ID", "USR_EML", "USR_TELNO", "USR_ADDR", "USR_DADDR",
                        "USR_ZIP", "USR_BANK_NM", "USR_ACNT_NO", "USR_ROLE_CD", "REVIEW");
    }
}
