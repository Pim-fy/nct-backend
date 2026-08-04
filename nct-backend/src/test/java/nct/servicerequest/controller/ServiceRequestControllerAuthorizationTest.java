package nct.servicerequest.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/** 담당자 7 통합: 서비스 요청 API의 일반회원·제공자 역할 경계 회귀 테스트. */
class ServiceRequestControllerAuthorizationTest {

    @Test
    void keepsRoleSpecificAuthorizationOnEveryServiceRequestEndpoint() {
        Map<String, String> expectedExpressions = Map.ofEntries(
                Map.entry("getActiveForms", "hasAuthority('ROLE_USER')"),
                Map.entry("getFormByTemplateSn", "hasAuthority('ROLE_USER')"),
                Map.entry("registerServiceRequest", "hasAuthority('ROLE_USER')"),
                Map.entry("updateServiceRequest", "hasAuthority('ROLE_USER')"),
                Map.entry("closeServiceRequest", "hasAuthority('ROLE_USER')"),
                Map.entry("searchServiceRequests", "hasAuthority('ROLE_SERVICE')"),
                Map.entry("getMyServiceRequests", "hasAuthority('ROLE_USER')"),
                Map.entry("getServiceRequest", "hasAnyAuthority('ROLE_USER', 'ROLE_SERVICE')"),
                Map.entry("getEditableServiceRequest", "hasAuthority('ROLE_USER')"),
                Map.entry("deleteServiceRequest", "hasAuthority('ROLE_USER')"));

        expectedExpressions.forEach((methodName, expectedExpression) -> {
            Method method = Arrays.stream(ServiceRequestController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

            assertThat(annotation)
                    .as("%s must declare @PreAuthorize", methodName)
                    .isNotNull();
            assertThat(annotation.value()).isEqualTo(expectedExpression);
        });
    }

    @Test
    void protectsServiceRequestImagesForUserAndProviderRoles() throws NoSuchMethodException {
        Method method = ServiceRequestImageController.class.getDeclaredMethod(
                "view", Long.class, Long.class, nct.global.security.domain.CustomUserDetails.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyAuthority('ROLE_USER', 'ROLE_SERVICE')");
    }
}
