package nct.abuse.controller;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import nct.abuse.dto.ManualAbuseReportRequest;
import nct.abuse.dto.ManualAbuseReportResponse;
import nct.abuse.dto.ManualAbuseReportStatusResponse;
import nct.abuse.service.AbuseReportService;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.global.security.port.AuthMember;

class AbuseReportControllerTest {

    @Test
    void createsManualReportWithAuthenticatedUserId() {
        AbuseReportService service = mock(AbuseReportService.class);
        AbuseReportController controller = new AbuseReportController(service);
        ManualAbuseReportRequest request = new ManualAbuseReportRequest(
                20L,
                "REFC0012",
                55L,
                "부적절한 문의입니다.");
        when(service.requestManualReport(10L, request))
                .thenReturn(new ManualAbuseReportResponse(501L));

        ResponseEntity<ApiResponse<ManualAbuseReportResponse>> response =
                controller.createManualReport(userDetails(10L), request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().reportSn()).isEqualTo(501L);
        verify(service).requestManualReport(10L, request);
    }

    @Test
    void returnsAuthenticatedUsersManualReportReferences() {
        AbuseReportService service = mock(AbuseReportService.class);
        AbuseReportController controller = new AbuseReportController(service);
        List<ManualAbuseReportStatusResponse> reports = List.of(
                new ManualAbuseReportStatusResponse(501L, 55L, "ABRC0005"));
        when(service.getMyManualReportReferences(10L, "REFC0012"))
                .thenReturn(reports);

        ResponseEntity<ApiResponse<List<ManualAbuseReportStatusResponse>>> response =
                controller.getMyReportReferences(userDetails(10L), "REFC0012");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).containsExactlyElementsOf(reports);
        verify(service).getMyManualReportReferences(10L, "REFC0012");
    }

    @Test
    void returnsActiveReportReferencesWithoutAuthenticatedUser() {
        AbuseReportService service = mock(AbuseReportService.class);
        AbuseReportController controller = new AbuseReportController(service);
        List<ManualAbuseReportStatusResponse> reports = List.of(
                new ManualAbuseReportStatusResponse(501L, 55L, "ABRC0005"));
        when(service.getActiveManualReportReferences(
                "REFC0012",
                List.of(55L, 56L)))
                .thenReturn(reports);

        ResponseEntity<ApiResponse<List<ManualAbuseReportStatusResponse>>> response =
                controller.getActiveReportReferences(
                        "REFC0012",
                        List.of(55L, 56L));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).containsExactlyElementsOf(reports);
        verify(service).getActiveManualReportReferences(
                "REFC0012",
                List.of(55L, 56L));
    }

    private CustomUserDetails userDetails(Long userId) {
        return new CustomUserDetails(AuthMember.builder()
                .id(userId)
                .email("seller@example.com")
                .password("{noop}test")
                .role("ROLE_USER")
                .status("USRC0001")
                .build());
    }
}
