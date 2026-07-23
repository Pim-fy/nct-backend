package nct.ops.operation.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import nct.global.security.domain.CustomUserDetails;
import nct.global.security.port.AuthMember;
import nct.abuse.dto.AdminAbuseReportResponse;
import nct.ops.operation.dto.AdminReportDecisionRequest;
import nct.ops.operation.port.AdminReportDecision;
import nct.ops.operation.service.AdminReportOperationService;

/** 담당자 7 · F-OPS-007: 관리자 신고 처리 컨트롤러 전달값을 검증합니다. */
class AdminReportOperationControllerTest {

    @Test
    void forwardsDecisionWithAuthenticatedAdmin() {
        AdminReportOperationService service = mock(AdminReportOperationService.class);
        AdminReportOperationController controller = new AdminReportOperationController(service);
        AdminReportDecisionRequest request = new AdminReportDecisionRequest();
        request.setDecision(AdminReportDecision.REJECTED);
        request.setReason(" insufficient evidence ");

        controller.decide(91L, request, adminUserDetails(7L));

        verify(service).decide(91L, AdminReportDecision.REJECTED, " insufficient evidence ", 7L);
    }

    @Test
    void returnsPendingReports() {
        AdminReportOperationService service = mock(AdminReportOperationService.class);
        AdminReportOperationController controller = new AdminReportOperationController(service);
        List<AdminAbuseReportResponse> reports = List.of(new AdminAbuseReportResponse());
        when(service.getPendingReports()).thenReturn(reports);

        controller.getPendingReports();

        verify(service).getPendingReports();
    }

    @Test
    void returnsReportDetail() {
        AdminReportOperationService service = mock(AdminReportOperationService.class);
        AdminReportOperationController controller = new AdminReportOperationController(service);
        AdminAbuseReportResponse report = new AdminAbuseReportResponse();
        when(service.getReportDetail(91L)).thenReturn(report);

        controller.getReportDetail(91L);

        verify(service).getReportDetail(91L);
    }

    private CustomUserDetails adminUserDetails(Long userId) {
        return new CustomUserDetails(AuthMember.builder()
                .id(userId)
                .email("admin@example.com")
                .password("{noop}test")
                .role("ROLE_ADMIN")
                .status("USRC0001")
                .build());
    }
}
