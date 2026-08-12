package nct.ops.sanction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import nct.abuse.domain.AbuseReport;
import nct.abuse.service.AbuseReportService;
import nct.auction.port.MemberAuctionEnforcementPort;
import nct.member.port.MemberStatusChangeCommand;
import nct.member.port.MemberStatusCommandPort;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.operation.domain.ReportEnforcementAction;
import nct.ops.operation.port.AdminReportDecision;
import nct.ops.sanction.domain.ReportSanctionCreateCommand;
import nct.ops.sanction.domain.SanctionRecord;
import nct.ops.sanction.domain.SanctionImpactRecord;
import nct.ops.sanction.mapper.SanctionImpactMapper;
import nct.quote.port.MemberQuoteEnforcementPort;
import nct.servicerequest.port.MemberServiceRequestEnforcementPort;
import nct.trade.port.MemberTradeRestrictionPort;

/** 담당자 7 · F-OPS-007/019/020: 신고 판정과 계정·업무 제재의 실행 순서를 검증합니다. */
class ReportEnforcementServiceTest {

    private AbuseReportService abuseReportService;
    private ReportSanctionService reportSanctionService;
    private SanctionImpactMapper sanctionImpactMapper;
    private MemberStatusCommandPort memberStatusCommandPort;
    private MemberAuctionEnforcementPort auctionEnforcementPort;
    private MemberServiceRequestEnforcementPort serviceRequestEnforcementPort;
    private MemberQuoteEnforcementPort quoteEnforcementPort;
    private MemberTradeRestrictionPort tradeRestrictionPort;
    private AuditLogPort auditLogPort;
    private ReportEnforcementService service;

    @BeforeEach
    void setUp() {
        abuseReportService = mock(AbuseReportService.class);
        reportSanctionService = mock(ReportSanctionService.class);
        sanctionImpactMapper = mock(SanctionImpactMapper.class);
        memberStatusCommandPort = mock(MemberStatusCommandPort.class);
        auctionEnforcementPort = mock(MemberAuctionEnforcementPort.class);
        serviceRequestEnforcementPort = mock(MemberServiceRequestEnforcementPort.class);
        quoteEnforcementPort = mock(MemberQuoteEnforcementPort.class);
        tradeRestrictionPort = mock(MemberTradeRestrictionPort.class);
        auditLogPort = mock(AuditLogPort.class);
        service = new ReportEnforcementService(
                abuseReportService,
                reportSanctionService,
                sanctionImpactMapper,
                memberStatusCommandPort,
                auctionEnforcementPort,
                serviceRequestEnforcementPort,
                quoteEnforcementPort,
                tradeRestrictionPort,
                auditLogPort,
                mock(NotificationService.class));
    }

    @Test
    void permanentSuspensionRunsSafeCancellationBeforeClosingRequestAndQuote() {
        AbuseReport report = new AbuseReport();
        report.setReportSn(501L);
        report.setReportedUserSn(11L);
        report.setStatusCode("ABSC0002");
        when(abuseReportService.lockForAdminDecision(501L)).thenReturn(report);

        SanctionRecord sanction = sanction(701L, 501L, 11L, null);
        when(reportSanctionService.create(any(ReportSanctionCreateCommand.class)))
                .thenReturn(sanction);
        when(auctionEnforcementPort.cancelForPermanentSuspension(any())).thenReturn(List.of());
        when(tradeRestrictionPort.enforcePermanent(any())).thenReturn(List.of());
        when(serviceRequestEnforcementPort.cancelOwnedForPermanentSuspension(any()))
                .thenReturn(List.of());
        when(quoteEnforcementPort.withdrawActiveQuotes(any(), any(), any())).thenReturn(List.of());

        service.decide(
                501L,
                AdminReportDecision.PROCESSED,
                ReportEnforcementAction.PERMANENT_SUSPENSION,
                "반복적인 거래 미이행",
                99L,
                "report-decision-501");

        InOrder order = inOrder(
                auctionEnforcementPort,
                tradeRestrictionPort,
                serviceRequestEnforcementPort,
                quoteEnforcementPort,
                abuseReportService);
        order.verify(auctionEnforcementPort).cancelForPermanentSuspension(any());
        order.verify(tradeRestrictionPort).enforcePermanent(any());
        order.verify(serviceRequestEnforcementPort).cancelOwnedForPermanentSuspension(any());
        order.verify(quoteEnforcementPort).withdrawActiveQuotes(any(), any(), any());
        order.verify(abuseReportService).decide(any());

        ArgumentCaptor<ReportSanctionCreateCommand> sanctionCaptor =
                ArgumentCaptor.forClass(ReportSanctionCreateCommand.class);
        verify(reportSanctionService).create(sanctionCaptor.capture());
        assertThat(sanctionCaptor.getValue().reportSn()).isEqualTo(501L);
        assertThat(sanctionCaptor.getValue().endAt()).isNull();
    }

    @Test
    void earlyReleaseRestoresAccountWhenNoOtherSuspensionRemains() {
        SanctionRecord sanction = sanction(
                701L,
                501L,
                11L,
                LocalDateTime.now().plusDays(3));
        when(reportSanctionService.findByReport(501L)).thenReturn(sanction);
        when(reportSanctionService.release(any()))
                .thenReturn(new ReportSanctionService.ReleaseResult(sanction, true));
        when(sanctionImpactMapper.findBySanctionForUpdate(701L)).thenReturn(List.of());
        when(reportSanctionService.hasOtherActiveSuspension(11L, 701L)).thenReturn(false);
        when(reportSanctionService.hasActiveSuspension(11L)).thenReturn(false);
        when(sanctionImpactMapper.findUnresolvedTemporaryByUserForUpdate(11L))
                .thenReturn(List.of());

        service.releaseByReport(501L, 99L, "오인 신고 확인", "report-release-501");

        ArgumentCaptor<MemberStatusChangeCommand> memberCaptor =
                ArgumentCaptor.forClass(MemberStatusChangeCommand.class);
        verify(memberStatusCommandPort).changeStatus(memberCaptor.capture());
        assertThat(memberCaptor.getValue().userSn()).isEqualTo(11L);
        assertThat(memberCaptor.getValue().targetStatusCode()).isEqualTo("USRC0001");
    }

    @Test
    void temporaryReleaseDoesNotRestoreReferenceHeldByAnotherActiveSanction() {
        SanctionImpactRecord impact = new SanctionImpactRecord();
        impact.setImpactSn(801L);
        impact.setSanctionSn(701L);
        impact.setReferenceTypeCode("REFC0005");
        impact.setReferenceSn(81L);
        impact.setActionCode("PAUSED");
        impact.setStatusCode("ACTIVE");
        impact.setPreviousStatusCode("TRDC0003");
        when(reportSanctionService.hasActiveSuspension(11L)).thenReturn(false);
        when(sanctionImpactMapper.findUnresolvedTemporaryByUserForUpdate(11L))
                .thenReturn(List.of(impact));
        when(sanctionImpactMapper.countOtherActiveBlockingImpacts(701L, "REFC0005", 81L))
                .thenReturn(1);
        when(sanctionImpactMapper.updateStatus(
                eq(801L), eq("ACTIVE"), eq("RELEASE_PENDING"), any(), any()))
                .thenReturn(1);

        service.restorePending(11L, 99L, "temporary restriction ended");

        verify(tradeRestrictionPort, never()).restoreTrade(any());
        verify(sanctionImpactMapper).updateStatus(
                eq(801L), eq("ACTIVE"), eq("RELEASE_PENDING"), any(), any());
    }

    @Test
    void automaticReleaseWritesSystemAuditActor() {
        SanctionRecord sanction = sanction(
                701L,
                501L,
                11L,
                LocalDateTime.now().minusMinutes(1));
        when(reportSanctionService.findByIdForUpdate(701L)).thenReturn(sanction);
        when(reportSanctionService.release(any()))
                .thenReturn(new ReportSanctionService.ReleaseResult(sanction, true));
        when(sanctionImpactMapper.findBySanctionForUpdate(701L)).thenReturn(List.of());
        when(reportSanctionService.hasOtherActiveSuspension(11L, 701L)).thenReturn(false);
        when(reportSanctionService.hasActiveSuspension(11L)).thenReturn(false);
        when(sanctionImpactMapper.findUnresolvedTemporaryByUserForUpdate(11L))
                .thenReturn(List.of());

        service.releaseExpired(701L);

        ArgumentCaptor<AuditLogCommand> auditCaptor =
                ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogPort).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().actorId()).isNull();
    }

    private SanctionRecord sanction(
            Long sanctionSn,
            Long reportSn,
            Long userSn,
            LocalDateTime endedAt) {
        SanctionRecord sanction = new SanctionRecord();
        sanction.setSanctionSn(sanctionSn);
        sanction.setSourceReportSn(reportSn);
        sanction.setUserSn(userSn);
        sanction.setProcessorUserSn(99L);
        sanction.setEndedAt(endedAt);
        return sanction;
    }
}
