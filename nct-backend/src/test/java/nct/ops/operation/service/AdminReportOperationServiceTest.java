package nct.ops.operation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nct.ops.operation.port.AdminReportDecision;
import nct.ops.operation.port.AdminReportDecisionCommand;
import nct.ops.operation.port.AdminReportDecisionPort;
import nct.abuse.dto.AdminAbuseReportResponse;
import nct.abuse.service.AbuseReportService;
import nct.global.response.PageResponse;
import nct.member.dto.AdminMemberIdentityResponse;
import nct.member.port.AdminMemberIdentityReader;
import nct.ops.operation.dto.AdminReportPageResponse;

/** 담당자 7 · F-OPS-007: 신고 처리 계약에 관리자·사유·결정값을 전달하는지 검증합니다. */
class AdminReportOperationServiceTest {

    private AdminReportDecisionPort adminReportDecisionPort;
    private AbuseReportService abuseReportService;
    private AdminMemberIdentityReader memberIdentityReader;
    private AdminReportOperationService service;

    @BeforeEach
    void setUp() {
        adminReportDecisionPort = mock(AdminReportDecisionPort.class);
        abuseReportService = mock(AbuseReportService.class);
        memberIdentityReader = mock(AdminMemberIdentityReader.class);
        when(memberIdentityReader.findByUserSns(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of());
        service = new AdminReportOperationService(
                adminReportDecisionPort, abuseReportService, memberIdentityReader);
    }

    @Test
    void forwardsProcessedDecisionWithNormalizedReason() {
        service.decide(91L, AdminReportDecision.PROCESSED, " confirmed by admin ", 7L);

        ArgumentCaptor<AdminReportDecisionCommand> commandCaptor =
                ArgumentCaptor.forClass(AdminReportDecisionCommand.class);
        verify(adminReportDecisionPort).decide(commandCaptor.capture());

        AdminReportDecisionCommand command = commandCaptor.getValue();
        assertThat(command.reportSn()).isEqualTo(91L);
        assertThat(command.decision()).isEqualTo(AdminReportDecision.PROCESSED);
        assertThat(command.reason()).isEqualTo("confirmed by admin");
        assertThat(command.adminId()).isEqualTo("7");
        assertThat(command.requestId()).startsWith("admin-report:");
    }

    @Test
    void delegatesPendingReportsToAbuseReportService() {
        service.getPendingReports();

        verify(abuseReportService).getPendingReports();
        verifyNoInteractions(memberIdentityReader);
    }

    @Test
    void mapsFilteredReportPageFromAbuseReportService() {
        AdminAbuseReportResponse report = new AdminAbuseReportResponse();
        when(abuseReportService.getAdminReports("ABRC0007", "신고", 2, 20))
                .thenReturn(PageResponse.<AdminAbuseReportResponse>builder()
                        .content(List.of(report))
                        .totalCount(21)
                        .page(2)
                        .size(20)
                        .hasNext(false)
                        .build());

        AdminReportPageResponse result = service.getReports("ABRC0007", "신고", 2, 20);

        assertThat(result.items()).containsExactly(report);
        assertThat(result.totalItems()).isEqualTo(21);
        assertThat(result.totalPages()).isEqualTo(2);
        verify(abuseReportService).getAdminReports("ABRC0007", "신고", 2, 20);
    }

    @Test
    void delegatesReportDetailToAbuseReportService() {
        service.getReportDetail(91L);

        verify(abuseReportService).getReportDetail(91L);
    }

    @Test
    void enrichesReportMembersWithoutPersonalContactFields() {
        AdminAbuseReportResponse report = new AdminAbuseReportResponse();
        report.setReporterUserSn(10L);
        report.setReportedUserSn(20L);
        AdminMemberIdentityResponse reporter = AdminMemberIdentityResponse.builder()
                .userSn(10L)
                .loginId("reporter01")
                .nickname("신고자")
                .build();
        AdminMemberIdentityResponse reported = AdminMemberIdentityResponse.builder()
                .userSn(20L)
                .loginId("reported01")
                .nickname("신고대상")
                .build();
        when(abuseReportService.getReportDetail(91L)).thenReturn(report);
        when(memberIdentityReader.findByUserSns(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of(10L, reporter, 20L, reported));

        AdminAbuseReportResponse result = service.getReportDetail(91L);

        assertThat(result.getReporterMember()).isSameAs(reporter);
        assertThat(result.getReportedMember()).isSameAs(reported);
    }
}
