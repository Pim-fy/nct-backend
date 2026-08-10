package nct.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import nct.global.security.domain.CustomUserDetails;
import nct.quote.controller.QuoteController;

/** 담당자 7 연동 · F-PROV-009: 활성 견적 집계 API의 경로와 제공자 권한을 확인합니다. */
class MyQuoteSummaryControllerContractTest {

    @Test
    void summaryEndpointRequiresExactServiceRole() throws NoSuchMethodException {
        Method endpoint = QuoteController.class.getDeclaredMethod(
                "getMyQuoteSummary",
                CustomUserDetails.class);

        GetMapping mapping = endpoint.getAnnotation(GetMapping.class);
        PreAuthorize authorization = endpoint.getAnnotation(PreAuthorize.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/me/summary");
        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAuthority('ROLE_SERVICE')");
    }
}
