package nct.servicerequest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ServiceRequestListImageMapperContractTest {

    private String normalizedMapperXml;

    @BeforeEach
    void loadMapperXml() throws IOException {
        ClassPathResource mapperResource = new ClassPathResource(
                "mapper/servicerequest/ServiceRequestMapper.xml");

        try (var inputStream = mapperResource.getInputStream()) {
            normalizedMapperXml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ");
        }
    }

    @Test
    @DisplayName("서비스 요청 목록은 첫 번째 활성 첨부 이미지를 대표 이미지로 반환한다")
    void serviceRequestListReturnsFirstActiveImage() {
        assertThat(normalizedMapperXml)
                .contains("property=\"thumbnailUrl\" column=\"THUMBNAIL_URL\"")
                .contains("ServiceRequestMapper.searchServiceRequests")
                .contains("file.FL_USE_YN = 'Y'")
                .contains("ORDER BY image.SVC_REQ_IMG_SORT_NO ASC, image.FL_SN ASC")
                .contains("AS THUMBNAIL_URL");
    }
}
