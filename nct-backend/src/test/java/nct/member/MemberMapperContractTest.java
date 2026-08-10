package nct.member;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** 담당자 7 · F-AUTH-010: 계좌 삭제가 한 UPDATE에서 두 컬럼에 함께 적용되는지 검증한다. */
class MemberMapperContractTest {

    @Test
    void profileUpdateClearsOrPreservesBankAccountPairAtomically() throws IOException {
        String mapper = new String(
                getClass().getResourceAsStream("/mapper/member/MemberMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        int start = mapper.indexOf("<update id=\"updateProfile\"");
        int end = mapper.indexOf("</update>", start);

        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        assertThat(mapper.substring(start, end))
                .contains("UPDATE USERS")
                .contains("<when test=\"clearBankAccount\">")
                .contains("USR_BANK_NM_ENC = NULL, USR_ACNT_NO_ENC = NULL")
                .contains("USR_BANK_NM_ENC = COALESCE(#{bankNameCiphertext}, USR_BANK_NM_ENC)")
                .contains("USR_ACNT_NO_ENC = COALESCE(#{accountNoCiphertext}, USR_ACNT_NO_ENC)")
                .contains("<when test=\"clearZip\"> USR_ZIP_ENC = NULL")
                .contains("<when test=\"clearAddress\"> USR_ADDR_ENC = NULL")
                .contains("<when test=\"clearAddressDetail\"> USR_DADDR_ENC = NULL")
                .contains("USR_ZIP_ENC = COALESCE(#{zipCiphertext}, USR_ZIP_ENC)")
                .contains("USR_ADDR_ENC = COALESCE(#{addressCiphertext}, USR_ADDR_ENC)")
                .contains("USR_DADDR_ENC = COALESCE(#{addressDetailCiphertext}, USR_DADDR_ENC)");
    }
}
