package nct.abuse.controller;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import nct.abuse.dto.CustomerAbuseReportRequest;
import nct.abuse.dto.ManualAbuseReportResponse;
import nct.abuse.dto.ManualAbuseReportStatusResponse;
import nct.abuse.service.AbuseReportService;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.global.security.port.AuthMember;

/** 담당자 7 · F-COM-018/TC-OPS-008: 현재 고객 신고 API와 공개 참조 상태 조회 계약을 검증합니다. */
class AbuseReportControllerTest {

    @Test
    void createsCustomerReportWithAuthenticatedUserId() {
        AbuseReportService service = mock(AbuseReportService.class);
        AbuseReportController controller = new AbuseReportController(service);
        CustomerAbuseReportRequest request = new CustomerAbuseReportRequest(
                "ABRC0001",
                20L,
                "REFC0003",
                55L,
                "종료 경매",
                "종료 경매 신고",
                "부적절한 경매입니다.",
                List.of());
        when(service.submitCustomerReport(10L, request))
                .thenReturn(new ManualAbuseReportResponse(501L));

        ResponseEntity<ApiResponse<ManualAbuseReportResponse>> response =
                controller.submitCustomerReport(userDetails(10L), request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().reportSn()).isEqualTo(501L);
        verify(service).submitCustomerReport(10L, request);
    }

    @Test
    void returnsActiveReportReferencesWithoutAuthenticatedUser() {
        AbuseReportService service = mock(AbuseReportService.class);
        AbuseReportController controller = new AbuseReportController(service);
        List<ManualAbuseReportStatusResponse> reports = List.of(
                new ManualAbuseReportStatusResponse(501L, 55L, "ABSC0001"));
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
