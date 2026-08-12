package nct.ops.operation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nct.ops.operation.port.AdminReportDecision;
import nct.abuse.dto.AdminAbuseReportResponse;
import nct.abuse.service.AbuseReportService;
import nct.global.response.PageResponse;
import nct.member.dto.AdminMemberIdentityResponse;
import nct.member.port.AdminMemberIdentityReader;
import nct.ops.operation.dto.AdminReportPageResponse;
import nct.ops.operation.domain.ReportEnforcementAction;
import nct.ops.sanction.mapper.SanctionImpactMapper;
import nct.ops.sanction.service.ReportEnforcementService;
import nct.ops.sanction.service.ReportSanctionService;

/** 담당자 7 · F-OPS-007: 신고 처리 계약에 관리자·사유·결정값을 전달하는지 검증합니다. */
class AdminReportOperationServiceTest {

    private AbuseReportService abuseReportService;
    private AdminMemberIdentityReader memberIdentityReader;
    private ReportEnforcementService reportEnforcementService;
    private ReportSanctionService reportSanctionService;
    private SanctionImpactMapper sanctionImpactMapper;
    private AdminReportOperationService service;

    @BeforeEach
    void setUp() {
        abuseReportService = mock(AbuseReportService.class);
        memberIdentityReader = mock(AdminMemberIdentityReader.class);
        reportEnforcementService = mock(ReportEnforcementService.class);
        reportSanctionService = mock(ReportSanctionService.class);
        sanctionImpactMapper = mock(SanctionImpactMapper.class);
        when(memberIdentityReader.findByUserSns(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of());
        service = new AdminReportOperationService(
                abuseReportService,
                memberIdentityReader,
                reportEnforcementService,
                reportSanctionService,
                sanctionImpactMapper);
    }

    @Test
    void forwardsProcessedDecisionWithNormalizedReason() {
        service.decide(
                91L,
                AdminReportDecision.PROCESSED,
                ReportEnforcementAction.TEMPORARY_SUSPENSION_7_DAYS,
                " confirmed by admin ",
                7L);

        ArgumentCaptor<String> requestIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(reportEnforcementService).decide(
                org.mockito.ArgumentMatchers.eq(91L),
                org.mockito.ArgumentMatchers.eq(AdminReportDecision.PROCESSED),
                org.mockito.ArgumentMatchers.eq(ReportEnforcementAction.TEMPORARY_SUSPENSION_7_DAYS),
                org.mockito.ArgumentMatchers.eq("confirmed by admin"),
                org.mockito.ArgumentMatchers.eq(7L),
                requestIdCaptor.capture());
        assertThat(requestIdCaptor.getValue()).startsWith("admin-report:");
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
