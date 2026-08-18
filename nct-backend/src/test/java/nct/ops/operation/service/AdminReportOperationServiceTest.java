package nct.ops.operation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nct.ops.operation.port.AdminReportDecision;
import nct.abuse.dto.AdminAbuseReportResponse;
import nct.abuse.domain.AbuseReport;
import nct.abuse.service.AbuseReportService;
import nct.abuse.service.ReportTargetHoldService;
import nct.global.exception.CustomException;
import nct.global.response.PageResponse;
import nct.member.dto.AdminMemberIdentityResponse;
import nct.member.port.AdminMemberIdentityReader;
import nct.ops.operation.dto.AdminReportPageResponse;
import nct.ops.operation.domain.AdminDisputeDecision;
import nct.ops.operation.domain.ReportEnforcementAction;
import nct.ops.sanction.mapper.SanctionImpactMapper;
import nct.ops.sanction.service.ReportEnforcementService;
import nct.ops.sanction.service.ReportSanctionService;

/** 담당자 7 · F-OPS-007: 신고 처리 계약에 관리자·사유·결정값을 전달하는지 검증합니다. */
class AdminReportOperationServiceTest {

    private AbuseReportService abuseReportService;
    private AdminMemberIdentityReader memberIdentityReader;
    private ReportEnforcementService reportEnforcementService;
    private AdminDisputeDecisionService tradeReportDecisionService;
    private ReportSanctionService reportSanctionService;
    private SanctionImpactMapper sanctionImpactMapper;
    private ReportTargetHoldService reportTargetHoldService;
    private AdminReportOperationService service;

    @BeforeEach
    void setUp() {
        abuseReportService = mock(AbuseReportService.class);
        memberIdentityReader = mock(AdminMemberIdentityReader.class);
        reportEnforcementService = mock(ReportEnforcementService.class);
        tradeReportDecisionService = mock(AdminDisputeDecisionService.class);
        reportSanctionService = mock(ReportSanctionService.class);
        sanctionImpactMapper = mock(SanctionImpactMapper.class);
        reportTargetHoldService = mock(ReportTargetHoldService.class);
        when(memberIdentityReader.findByUserSns(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of());
        service = new AdminReportOperationService(
                abuseReportService,
                memberIdentityReader,
                reportEnforcementService,
                tradeReportDecisionService,
                reportSanctionService,
                sanctionImpactMapper,
                reportTargetHoldService);
    }

    @Test
    void forwardsProcessedDecisionWithNormalizedReason() {
        when(abuseReportService.lockForAdminDecision(91L))
                .thenReturn(AbuseReport.builder()
                        .reportSn(91L)
                        .statusCode("ABSC0002")
                        .referenceTypeCode("REFC0003")
                        .referenceSn(301L)
                        .build());
        when(abuseReportService.hasTradeContext(91L)).thenReturn(false);

        service.decide(
                91L,
                AdminReportDecision.PROCESSED,
                null,
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
        verify(reportTargetHoldService, never()).pause(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
        verify(reportTargetHoldService).release(91L, "7");
        verifyNoInteractions(tradeReportDecisionService);
    }

    @Test
    void coordinatesTradeRefundBeforeFinalReportDecision() {
        when(abuseReportService.lockForAdminDecision(91L))
                .thenReturn(AbuseReport.builder().statusCode("ABSC0001").build());
        when(abuseReportService.hasTradeContext(91L)).thenReturn(true);

        service.decide(
                91L,
                AdminReportDecision.PROCESSED,
                AdminDisputeDecision.REFUND,
                ReportEnforcementAction.PERMANENT_SUSPENSION,
                " full refund ",
                7L);

        verify(reportEnforcementService).decide(
                eq(91L),
                eq(AdminReportDecision.PROCESSING),
                eq(ReportEnforcementAction.NONE),
                eq("full refund"),
                eq(7L),
                anyString());
        verify(tradeReportDecisionService).decide(
                91L, AdminDisputeDecision.REFUND, "full refund", 7L);
        verifyNoInteractions(reportTargetHoldService);
        verify(reportEnforcementService).decide(
                eq(91L),
                eq(AdminReportDecision.PROCESSED),
                eq(ReportEnforcementAction.PERMANENT_SUSPENSION),
                eq("full refund"),
                eq(7L),
                anyString());
    }

    @Test
    void rejectsTradeDecisionForGeneralReport() {
        when(abuseReportService.lockForAdminDecision(91L))
                .thenReturn(AbuseReport.builder().statusCode("ABSC0002").build());
        when(abuseReportService.hasTradeContext(91L)).thenReturn(false);

        assertThatThrownBy(() -> service.decide(
                91L,
                AdminReportDecision.PROCESSED,
                AdminDisputeDecision.COMPLETE,
                ReportEnforcementAction.NONE,
                "confirmed",
                7L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("일반 신고");
    }

    @Test
    void requiresMatchingTradeDecisionForTradeReport() {
        when(abuseReportService.lockForAdminDecision(91L))
                .thenReturn(AbuseReport.builder().statusCode("ABSC0001").build());
        when(abuseReportService.hasTradeContext(91L)).thenReturn(true);

        assertThatThrownBy(() -> service.decide(
                91L,
                AdminReportDecision.PROCESSING,
                AdminDisputeDecision.REFUND,
                ReportEnforcementAction.NONE,
                "still reviewing",
                7L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("처리 시작 단계");
    }

    @Test
    void startsGeneralAuctionReportAfterApplyingSingleTargetHold() {
        AbuseReport report = AbuseReport.builder()
                .reportSn(91L)
                .statusCode("ABSC0001")
                .referenceTypeCode("REFC0003")
                .referenceSn(301L)
                .build();
        when(abuseReportService.lockForAdminDecision(91L)).thenReturn(report);
        when(abuseReportService.hasTradeContext(91L)).thenReturn(false);

        service.decide(
                91L,
                AdminReportDecision.PROCESSING,
                null,
                ReportEnforcementAction.NONE,
                "확인 시작",
                7L);

        verify(reportTargetHoldService).pause(91L, "REFC0003", 301L, "7");
        verify(reportEnforcementService).decide(
                eq(91L), eq(AdminReportDecision.PROCESSING),
                eq(ReportEnforcementAction.NONE), eq("확인 시작"), eq(7L), anyString());
        verifyNoInteractions(tradeReportDecisionService);
    }

    @Test
    void startsTradeReportWithoutFinalTradeDecision() {
        AbuseReport report = AbuseReport.builder()
                .reportSn(91L)
                .statusCode("ABSC0001")
                .referenceTypeCode("REFC0005")
                .referenceSn(401L)
                .build();
        when(abuseReportService.lockForAdminDecision(91L)).thenReturn(report);
        when(abuseReportService.hasTradeContext(91L)).thenReturn(true);

        service.decide(
                91L,
                AdminReportDecision.PROCESSING,
                null,
                ReportEnforcementAction.NONE,
                "거래 확인 시작",
                7L);

        verify(reportTargetHoldService).pause(91L, "REFC0005", 401L, "7");
        verify(reportEnforcementService).decide(
                eq(91L), eq(AdminReportDecision.PROCESSING),
                eq(ReportEnforcementAction.NONE), eq("거래 확인 시작"), eq(7L), anyString());
        verifyNoInteractions(tradeReportDecisionService);
    }

    @Test
    void permanentSuspensionOfTradeReportRequiresRefund() {
        when(abuseReportService.lockForAdminDecision(91L))
                .thenReturn(AbuseReport.builder().statusCode("ABSC0001").build());
        when(abuseReportService.hasTradeContext(91L)).thenReturn(true);

        assertThatThrownBy(() -> service.decide(
                91L,
                AdminReportDecision.PROCESSED,
                AdminDisputeDecision.COMPLETE,
                ReportEnforcementAction.PERMANENT_SUSPENSION,
                "permanent suspension",
                7L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("전액 환불");
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
        when(abuseReportService.getAdminReports("ABSC0003", "신고", "GENERAL", 2, 20))
                .thenReturn(PageResponse.<AdminAbuseReportResponse>builder()
                        .content(List.of(report))
                        .totalCount(21)
                        .page(2)
                        .size(20)
                        .hasNext(false)
                        .build());

        AdminReportPageResponse result = service.getReports(
                "ABSC0003", "신고", "GENERAL", 2, 20);

        assertThat(result.items()).containsExactly(report);
        assertThat(result.totalItems()).isEqualTo(21);
        assertThat(result.totalPages()).isEqualTo(2);
        verify(abuseReportService).getAdminReports("ABSC0003", "신고", "GENERAL", 2, 20);
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
