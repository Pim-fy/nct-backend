package nct.abuse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
import java.util.Optional;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;

import nct.abuse.domain.AbuseReport;
import nct.abuse.dto.AdminAbuseReportResponse;
import nct.abuse.dto.CustomerAbuseReportRequest;
import nct.abuse.dto.ManualAbuseReportRequest;
import nct.abuse.dto.ManualAbuseReportResponse;
import nct.abuse.dto.ManualAbuseReportStatusResponse;
import nct.abuse.mapper.AbuseReportMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.PageResponse;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.operation.port.AdminReportDecision;
import nct.ops.operation.port.AdminReportDecisionCommand;
import nct.ops.reference.service.ReferenceDataService;
import nct.ops.security.port.SensitiveDetectionReportCommand;
import nct.ops.security.port.SensitiveDetectionReportResult;
import nct.ops.security.service.SensitiveDataType;
import nct.product.dto.InquiryReportTarget;
import nct.product.service.ProductService;

class AbuseReportServiceTest {

    private AbuseReportMapper abuseReportMapper;
    private ReferenceDataService referenceDataService;
    private AuditLogPort auditLogPort;
    private NotificationService notificationService;
    private ProductService productService;
    private ObjectProvider<ProductService> productServiceProvider;
    private AuthMemberPort authMemberPort;
    private AbuseReportService service;

    @BeforeEach
    void setUp() {
        abuseReportMapper = mock(AbuseReportMapper.class);
        referenceDataService = mock(ReferenceDataService.class);
        auditLogPort = mock(AuditLogPort.class);
        notificationService = mock(NotificationService.class);
        productService = mock(ProductService.class);
        productServiceProvider = mock(ObjectProvider.class);
        authMemberPort = mock(AuthMemberPort.class);
        when(productServiceProvider.getObject()).thenReturn(productService);
        when(abuseReportMapper.findManualReportId(any(), any(), any())).thenReturn(null);
        service = new AbuseReportService(
                abuseReportMapper,
                referenceDataService,
                auditLogPort,
                notificationService,
                productServiceProvider,
                authMemberPort);
    }

    @Test
    void rejectsCustomerReportForSelfOrMissingReportedUser() {
        CustomerAbuseReportRequest selfReport = customerReportRequest(10L);
        assertThatThrownBy(() -> service.submitCustomerReport(10L, selfReport))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        when(authMemberPort.findById(20L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submitCustomerReport(10L, customerReportRequest(20L)))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));

        verify(abuseReportMapper, never()).insertCustomerReport(any(AbuseReport.class));
    }

    @Test
    void createsCustomerReportForExistingReportedUser() {
        when(authMemberPort.findById(20L)).thenReturn(Optional.of(mock(AuthMember.class)));
        doAnswer(invocation -> {
            AbuseReport report = invocation.getArgument(0);
            report.setReportSn(501L);
            return 1;
        }).when(abuseReportMapper).insertCustomerReport(any(AbuseReport.class));

        ManualAbuseReportResponse result = service.submitCustomerReport(10L, customerReportRequest(20L));

        assertThat(result.reportSn()).isEqualTo(501L);
        verify(abuseReportMapper).insertCustomerReport(any(AbuseReport.class));
    }

    @Test
    void createsManualReportForBuyerInquiryOwnedBySeller() {
        InquiryReportTarget target = inquiryTarget(55L, 20L, 10L, "PRDC0006");
        when(productService.getInquiryReportTarget(55L)).thenReturn(target);
        doAnswer(invocation -> {
            AbuseReport report = invocation.getArgument(0);
            report.setReportSn(501L);
            return 1;
        }).when(abuseReportMapper).insertManualReport(any(AbuseReport.class));

        ManualAbuseReportResponse result = service.requestManualReport(
                10L,
                new ManualAbuseReportRequest(
                        20L,
                        AbuseReportService.PRODUCT_COMMENT_REFERENCE_TYPE,
                        55L,
                        "  부적절한 문의입니다.  "));

        assertThat(result.reportSn()).isEqualTo(501L);
        ArgumentCaptor<AbuseReport> reportCaptor = ArgumentCaptor.forClass(AbuseReport.class);
        verify(abuseReportMapper).insertManualReport(reportCaptor.capture());
        AbuseReport report = reportCaptor.getValue();
        assertThat(report.getRiskEventSn()).isNull();
        assertThat(report.getReporterUserSn()).isEqualTo(10L);
        assertThat(report.getReportedUserSn()).isEqualTo(20L);
        assertThat(report.getReportTypeCode()).isEqualTo(AbuseReportService.CONTENT_REPORT_TYPE);
        assertThat(report.getStatusCode()).isEqualTo(AbuseReportService.RECEIVED_STATUS);
        assertThat(report.getReferenceTypeCode())
                .isEqualTo(AbuseReportService.PRODUCT_COMMENT_REFERENCE_TYPE);
        assertThat(report.getReferenceSn()).isEqualTo(55L);
        assertThat(report.getContent()).isEqualTo("부적절한 문의입니다.");
        assertThat(report.getRegisteredBy()).isEqualTo("10");
        assertThat(report.getUpdatedBy()).isEqualTo("10");
        verify(referenceDataService).requireActiveCode("ABRG01", "ABRC0001");
        verify(referenceDataService).requireActiveCode("ABRG02", "ABRC0005");
        verify(referenceDataService).requireActiveCode("REFG01", "REFC0012");
    }

    @Test
    void rejectsDuplicateManualReport() {
        InquiryReportTarget target = inquiryTarget(55L, 20L, 10L, "PRDC0006");
        when(productService.getInquiryReportTarget(55L)).thenReturn(target);
        when(abuseReportMapper.findManualReportId(10L, "REFC0012", 55L))
                .thenReturn(501L);

        assertThatThrownBy(() -> service.requestManualReport(
                10L,
                new ManualAbuseReportRequest(20L, "REFC0012", 55L, "신고")))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ABUSE_REPORT_ALREADY_EXISTS));

        verify(abuseReportMapper, never()).insertManualReport(any(AbuseReport.class));
    }

    @Test
    void mapsConcurrentDuplicateManualReportToConflict() {
        InquiryReportTarget target = inquiryTarget(55L, 20L, 10L, "PRDC0006");
        when(productService.getInquiryReportTarget(55L)).thenReturn(target);
        when(abuseReportMapper.insertManualReport(any(AbuseReport.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> service.requestManualReport(
                10L,
                new ManualAbuseReportRequest(20L, "REFC0012", 55L, "신고")))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ABUSE_REPORT_ALREADY_EXISTS));
    }

    @Test
    void returnsManualReportReferencesForAuthenticatedReporter() {
        List<ManualAbuseReportStatusResponse> reports = List.of(
                new ManualAbuseReportStatusResponse(501L, 55L, "ABRC0005"));
        when(abuseReportMapper.findManualReportsByReporterAndReferenceType(
                10L,
                "REFC0012"))
                .thenReturn(reports);

        List<ManualAbuseReportStatusResponse> result =
                service.getMyManualReportReferences(10L, " REFC0012 ");

        assertThat(result).containsExactlyElementsOf(reports);
        verify(referenceDataService).requireActiveCode("REFG01", "REFC0012");
        verify(abuseReportMapper).findManualReportsByReporterAndReferenceType(
                10L,
                "REFC0012");
    }

    @Test
    void returnsActiveManualReportReferencesForPublicInquiryList() {
        List<ManualAbuseReportStatusResponse> reports = List.of(
                new ManualAbuseReportStatusResponse(501L, 55L, "ABRC0005"));
        when(abuseReportMapper.findActiveManualReportsByReferences(
                "REFC0012",
                List.of(55L, 56L),
                "ABRC0005",
                "ABRC0006"))
                .thenReturn(reports);

        List<ManualAbuseReportStatusResponse> result =
                service.getActiveManualReportReferences(
                        " REFC0012 ",
                        List.of(55L, 56L, 55L));

        assertThat(result).containsExactlyElementsOf(reports);
        verify(referenceDataService).requireActiveCode("REFG01", "REFC0012");
        verify(abuseReportMapper).findActiveManualReportsByReferences(
                "REFC0012",
                List.of(55L, 56L),
                "ABRC0005",
                "ABRC0006");
    }

    @Test
    void rejectsManualReportFromNonSellerOrWithManipulatedTargetUser() {
        InquiryReportTarget target = inquiryTarget(55L, 20L, 10L, "PRDC0006");
        when(productService.getInquiryReportTarget(55L)).thenReturn(target);

        assertThatThrownBy(() -> service.requestManualReport(
                11L,
                new ManualAbuseReportRequest(20L, "REFC0012", 55L, "신고")))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> service.requestManualReport(
                10L,
                new ManualAbuseReportRequest(21L, "REFC0012", 55L, "신고")))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verify(abuseReportMapper, never()).insertManualReport(any(AbuseReport.class));
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
    void returnsFilteredAdminReportPage() {
        AdminAbuseReportResponse processedReport = adminReport(
                101L,
                77L,
                null,
                AbuseReportService.PROCESSED_STATUS);
        when(abuseReportMapper.countAdminReports(
                AbuseReportService.PROCESSED_STATUS,
                "회원 20"))
                .thenReturn(21L);
        when(abuseReportMapper.findAdminReports(
                AbuseReportService.PROCESSED_STATUS,
                "회원 20",
                20L,
                20))
                .thenReturn(List.of(processedReport));

        PageResponse<AdminAbuseReportResponse> result = service.getAdminReports(
                " " + AbuseReportService.PROCESSED_STATUS + " ",
                " 회원 20 ",
                2,
                20);

        assertThat(result.getContent()).containsExactly(processedReport);
        assertThat(result.getTotalCount()).isEqualTo(21L);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.isHasNext()).isFalse();
        verify(referenceDataService).requireActiveCode(
                "ABRG02",
                AbuseReportService.PROCESSED_STATUS);
        verify(abuseReportMapper).findAdminReports(
                AbuseReportService.PROCESSED_STATUS,
                "회원 20",
                20L,
                20);
    }

    @Test
    void rejectsInvalidAdminReportPageRequest() {
        assertThatThrownBy(() -> service.getAdminReports(null, null, 0, 20))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> service.getAdminReports(null, "x".repeat(101), 1, 20))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verify(abuseReportMapper, never()).countAdminReports(any(), any());
        verify(abuseReportMapper, never()).findAdminReports(any(), any(), anyLong(), anyInt());
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
        verify(notificationService).notifyReportResult(10L, 101L, "처리 완료");
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
        verify(notificationService).notifyReportResult(10L, 101L, "반려");
    }

    @Test
    void doesNotNotifyUserForAutomaticReportWithoutReporter() {
        AbuseReport report = pendingReport(
                101L,
                AbuseReportService.RECEIVED_STATUS,
                null);
        when(abuseReportMapper.findReportByIdForUpdate(101L)).thenReturn(report);
        when(abuseReportMapper.updateDecision(
                101L,
                AbuseReportService.RECEIVED_STATUS,
                AbuseReportService.PROCESSED_STATUS,
                "자동 탐지 확인",
                "7")).thenReturn(1);

        service.decide(new AdminReportDecisionCommand(
                101L,
                AdminReportDecision.PROCESSED,
                "자동 탐지 확인",
                "7",
                "request-system-1"));

        verify(notificationService, never()).notifyReportResult(anyLong(), anyLong(), any());
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
            verify(notificationService, times(1))
                    .notifyReportResult(eq(10L), eq(101L), any());
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

    private CustomerAbuseReportRequest customerReportRequest(Long reportedUserSn) {
        return new CustomerAbuseReportRequest(
                AbuseReportService.CONTENT_REPORT_TYPE,
                reportedUserSn,
                null,
                null,
                null,
                "report title",
                "report content");
    }

    private InquiryReportTarget inquiryTarget(
            Long inquirySn,
            Long writerUserSn,
            Long sellerUserSn,
            String typeCode) {
        InquiryReportTarget target = mock(InquiryReportTarget.class);
        when(target.getPrdCmtSn()).thenReturn(inquirySn);
        when(target.getWriterUsrSn()).thenReturn(writerUserSn);
        when(target.getSellerUsrSn()).thenReturn(sellerUserSn);
        when(target.getPrdCmtTypeCd()).thenReturn(typeCode);
        return target;
    }

    private AbuseReport pendingReport(Long reportSn, String statusCode) {
        return pendingReport(reportSn, statusCode, 10L);
    }

    private AbuseReport pendingReport(
            Long reportSn,
            String statusCode,
            Long reporterUserSn) {
        return AbuseReport.builder()
                .reportSn(reportSn)
                .riskEventSn(77L)
                .reporterUserSn(reporterUserSn)
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
                AbuseReportService.PROCESSED_STATUS.equals(statusCode) ? "7" : null,
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
