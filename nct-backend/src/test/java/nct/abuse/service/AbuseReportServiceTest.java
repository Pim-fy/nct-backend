package nct.abuse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import nct.abuse.domain.AbuseReport;
import nct.abuse.dto.AdminAbuseReportResponse;
import nct.abuse.mapper.AbuseReportMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.operation.port.AdminReportDecision;
import nct.ops.operation.port.AdminReportDecisionCommand;
import nct.ops.reference.service.ReferenceDataService;
import nct.ops.security.port.SensitiveDetectionReportCommand;
import nct.ops.security.port.SensitiveDetectionReportResult;
import nct.ops.security.service.SensitiveDataType;

class AbuseReportServiceTest {

    private AbuseReportMapper abuseReportMapper;
    private ReferenceDataService referenceDataService;
    private AuditLogPort auditLogPort;
    private AbuseReportService service;

    @BeforeEach
    void setUp() {
        abuseReportMapper = mock(AbuseReportMapper.class);
        referenceDataService = mock(ReferenceDataService.class);
        auditLogPort = mock(AuditLogPort.class);
        service = new AbuseReportService(
                abuseReportMapper,
                referenceDataService,
                auditLogPort);
    }

    @Test
    void returnsPendingAutomaticAndManualReportsForAdminQuery() {
        AdminAbuseReportResponse automaticReport = adminReport(
                101L,
                77L,
                null,
                AbuseReportService.RECEIVED_STATUS);
        AdminAbuseReportResponse manualReport = adminReport(
                102L,
                null,
                10L,
                AbuseReportService.PROCESSING_STATUS);
        when(abuseReportMapper.findPendingReports(
                AbuseReportService.RECEIVED_STATUS,
                AbuseReportService.PROCESSING_STATUS))
                .thenReturn(List.of(automaticReport, manualReport));

        List<AdminAbuseReportResponse> result = service.getPendingReports();

        assertThat(result).containsExactly(automaticReport, manualReport);
        assertThat(result.get(0).getRiskEventSn()).isEqualTo(77L);
        assertThat(result.get(0).getReporterUserSn()).isNull();
        assertThat(result.get(1).getRiskEventSn()).isNull();
        assertThat(result.get(1).getReporterUserSn()).isEqualTo(10L);
        verify(abuseReportMapper).findPendingReports(
                AbuseReportService.RECEIVED_STATUS,
                AbuseReportService.PROCESSING_STATUS);
    }

    @Test
    void returnsReportDetailForAdminQuery() {
        AdminAbuseReportResponse report = adminReport(
                101L,
                77L,
                null,
                AbuseReportService.PROCESSED_STATUS);
        when(abuseReportMapper.findReportDetailById(101L)).thenReturn(report);

        AdminAbuseReportResponse result = service.getReportDetail(101L);

        assertThat(result).isSameAs(report);
        verify(abuseReportMapper).findReportDetailById(101L);
    }

    @Test
    void rejectsInvalidOrMissingReportDetail() {
        assertThatThrownBy(() -> service.getReportDetail(null))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> service.getReportDetail(0L))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(abuseReportMapper, never()).findReportDetailById(any());

        when(abuseReportMapper.findReportDetailById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.getReportDetail(404L))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ABUSE_REPORT_NOT_FOUND));
    }

    @Test
    void createsSystemReportFromSensitiveDetection() {
        doAnswer(invocation -> {
            AbuseReport report = invocation.getArgument(0);
            report.setReportSn(101L);
            return 1;
        }).when(abuseReportMapper).insertAutomaticReport(any(AbuseReport.class));

        SensitiveDetectionReportResult result = service.requestReport(reportCommand(77L));

        assertThat(result.status()).isEqualTo(SensitiveDetectionReportResult.Status.CREATED);
        assertThat(result.reportSn()).isEqualTo(101L);
        ArgumentCaptor<AbuseReport> reportCaptor = ArgumentCaptor.forClass(AbuseReport.class);
        verify(abuseReportMapper).insertAutomaticReport(reportCaptor.capture());
        AbuseReport report = reportCaptor.getValue();
        assertThat(report.getRiskEventSn()).isEqualTo(77L);
        assertThat(report.getReporterUserSn()).isNull();
        assertThat(report.getReportedUserSn()).isNull();
        assertThat(report.getReportTypeCode()).isEqualTo(AbuseReportService.CONTENT_REPORT_TYPE);
        assertThat(report.getStatusCode()).isEqualTo(AbuseReportService.RECEIVED_STATUS);
        assertThat(report.getReferenceTypeCode()).isEqualTo("REFC0005");
        assertThat(report.getReferenceSn()).isEqualTo(31L);
        assertThat(report.getContent()).isEqualTo("민감정보 자동 탐지: EMAIL,PHONE_NUMBER");
        assertThat(report.getRegisteredBy()).isEqualTo("SYSTEM");
        assertThat(report.getUpdatedBy()).isEqualTo("SYSTEM");
        verify(referenceDataService).requireActiveCode("ABRG01", "ABRC0001");
        verify(referenceDataService).requireActiveCode("ABRG02", "ABRC0005");
        verify(referenceDataService).requireActiveCode("REFG01", "REFC0005");
    }

    @Test
    void reusesReportWhenRiskEventAlreadyHasOne() {
        when(abuseReportMapper.insertAutomaticReport(any()))
                .thenThrow(new DuplicateKeyException("duplicate risk event"));
        when(abuseReportMapper.findReportIdByRiskEventIdForUpdate(77L)).thenReturn(101L);

        SensitiveDetectionReportResult result = service.requestReport(reportCommand(77L));

        assertThat(result.status()).isEqualTo(SensitiveDetectionReportResult.Status.REUSED);
        assertThat(result.reportSn()).isEqualTo(101L);
    }

    @Test
    void concurrentAutomaticReportsCreateOnlyOneResult() throws Exception {
        AtomicBoolean inserted = new AtomicBoolean();
        when(abuseReportMapper.insertAutomaticReport(any())).thenAnswer(invocation -> {
            AbuseReport report = invocation.getArgument(0);
            if (inserted.compareAndSet(false, true)) {
                report.setReportSn(101L);
                return 1;
            }
            throw new DuplicateKeyException("duplicate risk event");
        });
        when(abuseReportMapper.findReportIdByRiskEventIdForUpdate(77L)).thenReturn(101L);

        int workers = 12;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<SensitiveDetectionReportResult>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < workers; index++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return service.requestReport(reportCommand(77L));
                }));
            }
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<SensitiveDetectionReportResult> results = new ArrayList<>();
            for (Future<SensitiveDetectionReportResult> future : futures) {
                results.add(future.get(3, TimeUnit.SECONDS));
            }
            assertThat(results).allSatisfy(result -> assertThat(result.reportSn()).isEqualTo(101L));
            assertThat(results).filteredOn(result ->
                    result.status() == SensitiveDetectionReportResult.Status.CREATED).hasSize(1);
            assertThat(results).filteredOn(result ->
                    result.status() == SensitiveDetectionReportResult.Status.REUSED).hasSize(workers - 1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void processesReportAndRecordsAuditDetails() {
        AbuseReport report = pendingReport(101L, AbuseReportService.RECEIVED_STATUS);
        when(abuseReportMapper.findReportByIdForUpdate(101L)).thenReturn(report);
        when(abuseReportMapper.updateDecision(
                101L,
                AbuseReportService.RECEIVED_STATUS,
                AbuseReportService.PROCESSED_STATUS,
                "위반 확인",
                "7")).thenReturn(1);

        service.decide(new AdminReportDecisionCommand(
                101L,
                AdminReportDecision.PROCESSED,
                " 위반 확인 ",
                "USR:7",
                "request-1"));

        ArgumentCaptor<AuditLogCommand> auditCaptor = ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogPort).record(auditCaptor.capture());
        AuditLogCommand audit = auditCaptor.getValue();
        assertThat(audit.actionCode()).isEqualTo("ADMIN_APPROVE");
        assertThat(audit.actorId()).isEqualTo("7");
        assertThat(audit.referenceTypeCode()).isEqualTo("REFC0005");
        assertThat(audit.referenceSn()).isEqualTo(31L);
        assertThat(audit.reason()).isEqualTo("위반 확인");
        assertThat(audit.beforeSummary()).isEqualTo("reportSn=101,status=ABRC0005");
        assertThat(audit.afterSummary()).isEqualTo("reportSn=101,status=ABRC0007");
        assertThat(audit.requestId()).isEqualTo("request-1");
    }

    @Test
    void rejectsProcessingReportAndStoresReason() {
        AbuseReport report = pendingReport(101L, AbuseReportService.PROCESSING_STATUS);
        when(abuseReportMapper.findReportByIdForUpdate(101L)).thenReturn(report);
        when(abuseReportMapper.updateDecision(
                101L,
                AbuseReportService.PROCESSING_STATUS,
                AbuseReportService.REJECTED_STATUS,
                "위반 아님",
                "7")).thenReturn(1);

        service.decide(new AdminReportDecisionCommand(
                101L,
                AdminReportDecision.REJECTED,
                "위반 아님",
                "7",
                "request-2"));

        verify(abuseReportMapper).updateDecision(
                101L,
                AbuseReportService.PROCESSING_STATUS,
                AbuseReportService.REJECTED_STATUS,
                "위반 아님",
                "7");
        ArgumentCaptor<AuditLogCommand> auditCaptor = ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogPort).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().actionCode()).isEqualTo("ADMIN_REJECT");
    }

    @Test
    void rejectsMissingOrAlreadyProcessedReport() {
        when(abuseReportMapper.findReportByIdForUpdate(404L)).thenReturn(null);
        assertThatThrownBy(() -> service.decide(decisionCommand(404L)))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ABUSE_REPORT_NOT_FOUND));

        when(abuseReportMapper.findReportByIdForUpdate(101L))
                .thenReturn(pendingReport(101L, AbuseReportService.PROCESSED_STATUS));
        assertThatThrownBy(() -> service.decide(decisionCommand(101L)))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ABUSE_REPORT_ALREADY_PROCESSED));
        verify(auditLogPort, never()).record(any());
    }

    @Test
    void rejectsBlankDecisionReason() {
        assertThatThrownBy(() -> service.decide(new AdminReportDecisionCommand(
                101L,
                AdminReportDecision.PROCESSED,
                "  ",
                "7",
                "request-1")))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(abuseReportMapper, never()).findReportByIdForUpdate(any());
    }

    @Test
    void concurrentDecisionsAllowOnlyOneFinalState() throws Exception {
        when(abuseReportMapper.findReportByIdForUpdate(101L)).thenAnswer(invocation ->
                pendingReport(101L, AbuseReportService.RECEIVED_STATUS));
        AtomicBoolean updated = new AtomicBoolean();
        when(abuseReportMapper.updateDecision(
                eq(101L),
                eq(AbuseReportService.RECEIVED_STATUS),
                any(),
                any(),
                eq("7"))).thenAnswer(invocation -> updated.compareAndSet(false, true) ? 1 : 0);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> processed = pool.submit(() -> decideConcurrently(
                    AdminReportDecision.PROCESSED, ready, start, failures));
            Future<?> rejected = pool.submit(() -> decideConcurrently(
                    AdminReportDecision.REJECTED, ready, start, failures));
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            processed.get(3, TimeUnit.SECONDS);
            rejected.get(3, TimeUnit.SECONDS);

            assertThat(failures).hasSize(1);
            assertThat(failures.peek()).isInstanceOfSatisfying(CustomException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
            verify(auditLogPort, times(1)).record(any());
        } finally {
            pool.shutdownNow();
        }
    }

    private void decideConcurrently(
            AdminReportDecision decision,
            CountDownLatch ready,
            CountDownLatch start,
            ConcurrentLinkedQueue<Throwable> failures) {
        ready.countDown();
        try {
            start.await();
            service.decide(new AdminReportDecisionCommand(
                    101L,
                    decision,
                    decision.name(),
                    "7",
                    "request-" + decision.name()));
        } catch (Throwable throwable) {
            failures.add(throwable);
        }
    }

    private SensitiveDetectionReportCommand reportCommand(Long riskEventSn) {
        return new SensitiveDetectionReportCommand(
                riskEventSn,
                " REFC0005 ",
                31L,
                Set.of(SensitiveDataType.PHONE_NUMBER, SensitiveDataType.EMAIL),
                "SYSTEM");
    }

    private AbuseReport pendingReport(Long reportSn, String statusCode) {
        return AbuseReport.builder()
                .reportSn(reportSn)
                .riskEventSn(77L)
                .statusCode(statusCode)
                .referenceTypeCode("REFC0005")
                .referenceSn(31L)
                .build();
    }

    private AdminAbuseReportResponse adminReport(
            Long reportSn,
            Long riskEventSn,
            Long reporterUserSn,
            String statusCode) {
        return new AdminAbuseReportResponse(
                reportSn,
                riskEventSn,
                reporterUserSn,
                20L,
                AbuseReportService.CONTENT_REPORT_TYPE,
                statusCode,
                "신고 내용",
                "REFC0005",
                31L,
                AbuseReportService.PROCESSED_STATUS.equals(statusCode) ? "처리 완료" : null,
                LocalDateTime.of(2026, 7, 23, 9, 0),
                AbuseReportService.PROCESSED_STATUS.equals(statusCode)
                        ? LocalDateTime.of(2026, 7, 23, 10, 0)
                        : null);
    }

    private AdminReportDecisionCommand decisionCommand(Long reportSn) {
        return new AdminReportDecisionCommand(
                reportSn,
                AdminReportDecision.PROCESSED,
                "위반 확인",
                "7",
                "request-1");
    }
}
