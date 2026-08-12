package nct.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 담당자 7 · F-OPS-006: 분쟁 복구 시 첫 완료 확인자 보존 SQL 계약을 검증한다. */
class AdminTradeDisputeCommandMapperContractTest {

    @Test
    void preservesFirstCompletionRequesterWhenRestoringWaitingConfirmationStatus()
            throws IOException {
        String mapper = loadNormalizedMapper();
        String updateStatement = mapper.substring(
                mapper.indexOf("<update id=\"updateTradeStatus\">"),
                mapper.indexOf("</update>", mapper.indexOf("<update id=\"updateTradeStatus\">")));

        assertThat(updateStatement)
                .contains("WHEN #{targetStatusCode} = 'TRDC0005' THEN TRD_UPDT_ID")
                .contains("ELSE #{updaterId}")
                .contains("TRD_STATUS_CD = #{targetStatusCode}")
                .contains("TRD_STATUS_CD = #{expectedStatusCode}");
    }

    private String loadNormalizedMapper() throws IOException {
        ClassPathResource resource =
                new ClassPathResource("mapper/trade/AdminTradeDisputeCommandMapper.xml");
        return resource.getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
