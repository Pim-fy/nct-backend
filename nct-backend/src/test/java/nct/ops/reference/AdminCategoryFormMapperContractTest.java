package nct.ops.reference;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 담당자 7 · F-COM-003/F-SVC-002: 카테고리 중복과 폼 초안 폐기 SQL 경계를 검증한다. */
class AdminCategoryFormMapperContractTest {

    @Test
    void categoryNameDuplicateCheckNormalizesWhitespaceAndCase() throws IOException {
        String mapper = loadNormalized("mapper/ops/reference/CategoryMapper.xml");

        assertThat(mapper)
                .contains("LOWER(TRIM(CAT_NM)) = LOWER(TRIM(#{name}))")
                .contains("CAT_DOMAIN_CD = #{domainCode}")
                .contains("CAT_PARENT_SN IS NOT NULL");
    }

    @Test
    void draftDiscardNeverUpdatesActiveTemplate() throws IOException {
        String mapper = loadNormalized("mapper/servicerequest/ServiceRequestFormMapper.xml");

        assertThat(mapper)
                .contains("F.FORM_VER_NO &gt; COALESCE")
                .contains("<update id=\"activateTemplate\">")
                .contains("<update id=\"discardDraft\">")
                .contains("FORM_USE_YN = 'N'")
                .contains("FORM_ACTV_YN = 'N'")
                .contains("FORM_VER_NO &gt; #{activeVersion}")
                .contains("SVC_REQ_FORM_TMPL_SN = #{formTemplateSn}");
    }

    private String loadNormalized(String classpathLocation) throws IOException {
        ClassPathResource mapperResource = new ClassPathResource(classpathLocation);
        try (var inputStream = mapperResource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ");
        }
    }
}
