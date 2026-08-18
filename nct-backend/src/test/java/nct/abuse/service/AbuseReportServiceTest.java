package nct.abuse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;

import nct.abuse.domain.AbuseReport;
import nct.abuse.dto.AdminAbuseReportResponse;
import nct.abuse.dto.CustomerAbuseReportRequest;
import nct.abuse.dto.CustomerSupportReportRequest;
import nct.abuse.dto.ManualAbuseReportResponse;
import nct.abuse.dto.ManualAbuseReportStatusResponse;
import nct.abuse.mapper.AbuseReportMapper;
import nct.abuse.port.TradeIncidentReportCommand;
import nct.auction.port.AuctionReferenceTitleReader;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.PageResponse;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.file.service.FileStorageService;
import nct.member.port.MemberOperationLockPort;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.operation.port.AdminReportDecision;
import nct.ops.operation.port.AdminReportDecisionCommand;
import nct.ops.reference.service.ReferenceDataService;
import nct.ops.risk.service.RiskEventService;
import nct.ops.risk.service.RiskSignalPublisher;
import nct.ops.security.port.SensitiveDetectionReportCommand;
import nct.ops.security.port.SensitiveDetectionReportResult;
import nct.ops.security.service.SensitiveDataType;
import nct.product.dto.InquiryReportTarget;
import nct.product.service.ProductService;
import nct.servicerequest.port.ServiceRequestQuoteReader;
import nct.trade.port.AdminReportTradeReferenceReader;

/** 담당자 7 · F-COM-018/F-OPS-007~008: 고객 신고 생성·상태 처리·중복 방지 계약을 검증합니다. */
class AbuseReportServiceTest {

    private AbuseReportMapper abuseReportMapper;
    private AuctionReferenceTitleReader auctionReferenceTitleReader;
    private ServiceRequestQuoteReader serviceRequestQuoteReader;
    private AdminReportTradeReferenceReader tradeReferenceReader;
    private ReferenceDataService referenceDataService;
    private AbuseReportReferenceValidationService referenceValidationService;
    private AuditLogPort auditLogPort;
    private NotificationService notificationService;
    private ProductService productService;
    private ObjectProvider<ProductService> productServiceProvider;
    private AuthMemberPort authMemberPort;
    private MemberOperationLockPort memberOperationLockPort;
    private FileStorageService fileStorageService;
    private RiskEventService riskEventService;
    private RiskSignalPublisher riskSignalPublisher;
    private ReportTargetHoldService reportTargetHoldService;
    private AbuseReportService service;

    @BeforeEach
    void setUp() {
        abuseReportMapper = mock(AbuseReportMapper.class);
        auctionReferenceTitleReader = mock(AuctionReferenceTitleReader.class);
        serviceRequestQuoteReader = mock(ServiceRequestQuoteReader.class);
        tradeReferenceReader = mock(AdminReportTradeReferenceReader.class);
        referenceDataService = mock(ReferenceDataService.class);
        referenceValidationService = mock(AbuseReportReferenceValidationService.class);
        auditLogPort = mock(AuditLogPort.class);
        notificationService = mock(NotificationService.class);
        productService = mock(ProductService.class);
        productServiceProvider = new ObjectProvider<>() {
            @Override
            public ProductService getObject() {
                return productService;
            }
        };
        authMemberPort = mock(AuthMemberPort.class);
        memberOperationLockPort = mock(MemberOperationLockPort.class);
        fileStorageService = mock(FileStorageService.class);
        riskEventService = mock(RiskEventService.class);
        riskSignalPublisher = mock(RiskSignalPublisher.class);
        reportTargetHoldService = mock(ReportTargetHoldService.class);
        when(referenceValidationService.requireValid(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Long reportedUserSn = invocation.getArgument(1);
                    return reportedUserSn == null ? null : "회원 #" + reportedUserSn;
                });
        when(abuseReportMapper.findManualReportId(any(), any(), any())).thenReturn(null);
        when(abuseReportMapper.findActiveCustomerReportId(
                any(), any(), any(), any(), any(), any(), any())).thenReturn(null);
        when(authMemberPort.findById(anyLong())).thenReturn(Optional.of(mock(AuthMember.class)));
        when(tradeReferenceReader.findByTradeSns(any())).thenReturn(Map.of());
        service = new AbuseReportService(
                abuseReportMapper,
                auctionReferenceTitleReader,
                serviceRequestQuoteReader,
                tradeReferenceReader,
                referenceDataService,
                referenceValidationService,
                auditLogPort,
                notificationService,
                productServiceProvider,
                authMemberPort,
                memberOperationLockPort,
                fileStorageService,
                riskEventService,
                riskSignalPublisher,
                reportTargetHoldService);
    }

    @Test
    void rejectsCustomerReportForSelfOrMissingReportedUser() {
        CustomerAbuseReportRequest selfReport = customerReportRequest(10L);
        assertThatThrownBy(() -> service.submitCustomerReport(10L, selfReport))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        doThrow(new CustomException(ErrorCode.USER_NOT_FOUND))
                .when(memberOperationLockPort).lock(20L);
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
        InOrder order = inOrder(memberOperationLockPort, abuseReportMapper);
        order.verify(memberOperationLockPort).lock(20L);
        order.verify(abuseReportMapper).insertCustomerReport(any(AbuseReport.class));
        verify(reportTargetHoldService).pause(501L, "REFC0001", 20L, "10");
    }

    /** 담당자 7 · REQ-COM-020: 신고 대상 확인은 개인정보 복호화 없이 잠금 조회로 끝냅니다. */
    @Test
    void createsCustomerReportWithoutDecryptingReportedUserIdentity() {
        when(authMemberPort.findById(20L))
                .thenThrow(new CustomException(ErrorCode.DATABASE_ERROR));
        doAnswer(invocation -> {
            AbuseReport report = invocation.getArgument(0);
            report.setReportSn(503L);
            return 1;
        }).when(abuseReportMapper).insertCustomerReport(any(AbuseReport.class));

        ManualAbuseReportResponse result =
                service.submitCustomerReport(10L, customerReportRequest(20L));

        assertThat(result.reportSn()).isEqualTo(503L);
        verify(authMemberPort, never()).findById(20L);
        verify(memberOperationLockPort).lock(20L);
    }

    @Test
    void createsTradeIncidentAsReportAndLinksValidatedEvidence() {
        doAnswer(invocation -> {
            AbuseReport report = invocation.getArgument(0);
            report.setReportSn(601L);
            return 1;
        }).when(abuseReportMapper).insertCustomerReport(any(AbuseReport.class));
        when(abuseReportMapper.insertReportFile(601L, 801L, 0, "11")).thenReturn(1);
        when(abuseReportMapper.insertTradeContext(
                601L, 81L, "TRDC0005", 3600L, true, true, "11"))
                .thenReturn(1);

        Long reportSn = service.create(new TradeIncidentReportCommand(
                81L,
                11L,
                22L,
                "ABRC0011",
                "보관금 반환이 필요합니다.",
                List.of(801L),
                "TRDC0005",
                3600L,
                true,
                true));

        assertThat(reportSn).isEqualTo(601L);
        InOrder order = inOrder(memberOperationLockPort, abuseReportMapper);
        order.verify(memberOperationLockPort).lock(22L);
        order.verify(abuseReportMapper).insertCustomerReport(any(AbuseReport.class));
        ArgumentCaptor<AbuseReport> captor = ArgumentCaptor.forClass(AbuseReport.class);
        verify(abuseReportMapper).insertCustomerReport(captor.capture());
        assertThat(captor.getValue().getReportTypeCode()).isEqualTo("ABRC0011");
        assertThat(captor.getValue().getReferenceTypeCode()).isEqualTo("REFC0005");
        assertThat(captor.getValue().getReferenceSn()).isEqualTo(81L);
        assertThat(captor.getValue().getReportedUserSn()).isEqualTo(22L);
        verify(abuseReportMapper).insertReportFile(601L, 801L, 0, "11");
        verify(abuseReportMapper).insertTradeContext(
                601L, 81L, "TRDC0005", 3600L, true, true, "11");
    }

    @Test
    void acceptsOnlyTheSevenDetailedCustomerReportTypes() {
        doAnswer(invocation -> {
            AbuseReport report = invocation.getArgument(0);
            report.setReportSn(501L);
            return 1;
        }).when(abuseReportMapper).insertCustomerReport(any(AbuseReport.class));
        List<String> reportTypes = List.of(
                AbuseReportService.FALSE_INFORMATION_FRAUD_REPORT_TYPE,
                AbuseReportService.EXTERNAL_CONTACT_PAYMENT_REPORT_TYPE,
                AbuseReportService.ABUSE_HARASSMENT_REPORT_TYPE,
                AbuseReportService.PROHIBITED_ILLEGAL_REPORT_TYPE,
                AbuseReportService.PRIVACY_REPORT_TYPE,
                AbuseReportService.SPAM_ADVERTISEMENT_REPORT_TYPE,
                AbuseReportService.OTHER_REPORT_TYPE);

        for (String reportType : reportTypes) {
            CustomerAbuseReportRequest request = new CustomerAbuseReportRequest(
                    reportType,
                    null,
                    null,
                    null,
                    "신고 대상",
                    "신고 제목",
                    "신고 내용",
                    List.of());

            service.submitCustomerReport(10L, request);

            verify(referenceDataService).requireActiveCode("ABRG01", reportType);
        }
        verify(abuseReportMapper, times(7)).insertCustomerReport(any(AbuseReport.class));
    }

    @Test
    void rejectsTradeOnlyTypesFromGeneralCustomerReportEndpoint() {
        List<String> invalidTypes = List.of(
                "ABRC0008",
                "ABRC0009",
                "ABRC0010",
                "ABRC0011");

        assertCustomerReportTypesAreRejected(invalidTypes);
    }

    @Test
    void rejectsStatusAndUnknownCodesFromGeneralCustomerReportEndpoint() {
        List<String> invalidTypes = List.of(
                "ABRC0012",
                "ABSC0001",
                "ABRC9999");

        assertCustomerReportTypesAreRejected(invalidTypes);
    }

    private void assertCustomerReportTypesAreRejected(List<String> invalidTypes) {

        for (String reportType : invalidTypes) {
            CustomerAbuseReportRequest request = new CustomerAbuseReportRequest(
                    reportType,
                    null,
                    null,
                    null,
                    "신고 대상",
                    "신고 제목",
                    "신고 내용",
                    List.of());

            assertThatThrownBy(() -> service.submitCustomerReport(10L, request))
                    .isInstanceOfSatisfying(CustomException.class, exception ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        }
        verify(abuseReportMapper, never()).insertCustomerReport(any(AbuseReport.class));
    }

    @Test
    void createsTargetlessGeneralReportWithoutMemberOrReference() {
        doAnswer(invocation -> {
            AbuseReport report = invocation.getArgument(0);
            report.setReportSn(502L);
            return 1;
        }).when(abuseReportMapper).insertCustomerReport(any(AbuseReport.class));
        CustomerAbuseReportRequest request = new CustomerAbuseReportRequest(
                AbuseReportService.FALSE_INFORMATION_FRAUD_REPORT_TYPE,
                null,
                null,
                null,
                "직접 입력 대상",
                "신고 제목",
                "신고 내용",
                List.of());

        ManualAbuseReportResponse result = service.submitCustomerReport(10L, request);

        assertThat(result.reportSn()).isEqualTo(502L);
        ArgumentCaptor<AbuseReport> reportCaptor = ArgumentCaptor.forClass(AbuseReport.class);
        verify(abuseReportMapper).insertCustomerReport(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getReportedUserSn()).isNull();
        assertThat(reportCaptor.getValue().getReferenceTypeCode()).isNull();
        assertThat(reportCaptor.getValue().getReferenceSn()).isNull();
        assertThat(reportCaptor.getValue().getTargetName()).isEqualTo("직접 입력 대상");
    }

    @Test
    void rejectsReportedUserWithoutAReferenceTriple() {
        CustomerAbuseReportRequest request = new CustomerAbuseReportRequest(
                AbuseReportService.FALSE_INFORMATION_FRAUD_REPORT_TYPE,
                20L,
                null,
                null,
                "임의 대상명",
                "신고 제목",
                "신고 내용",
                List.of());

        assertThatThrownBy(() -> service.submitCustomerReport(10L, request))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verify(abuseReportMapper, never()).insertCustomerReport(any(AbuseReport.class));
    }

    @Test
    void linksOnlyOwnedAbuseReportFilesInRequestOrder() {
        doAnswer(invocation -> {
            AbuseReport report = invocation.getArgument(0);
            report.setReportSn(501L);
            return 1;
        }).when(abuseReportMapper).insertCustomerReport(any(AbuseReport.class));
        when(abuseReportMapper.insertReportFile(anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(1);
        CustomerAbuseReportRequest request = new CustomerAbuseReportRequest(
                AbuseReportService.FALSE_INFORMATION_FRAUD_REPORT_TYPE,
                20L,
                "REFC0001",
                20L,
                "신고 대상",
                "신고 제목",
                "신고 내용",
                List.of(802L, 801L));

        service.submitCustomerReport(10L, request);

        InOrder lockOrder = inOrder(fileStorageService);
        lockOrder.verify(fileStorageService).requireOwnedAbuseReportFile(801L, 10L);
        lockOrder.verify(fileStorageService).requireOwnedAbuseReportFile(802L, 10L);
        verify(abuseReportMapper).insertReportFile(501L, 802L, 0, "10");
        verify(abuseReportMapper).insertReportFile(501L, 801L, 1, "10");
    }

    @Test
    void rejectsActiveDuplicateCustomerReport() {
        when(abuseReportMapper.findActiveCustomerReportId(
                10L,
                20L,
                AbuseReportService.FALSE_INFORMATION_FRAUD_REPORT_TYPE,
                "REFC0001",
                20L,
                AbuseReportService.RECEIVED_STATUS,
                AbuseReportService.PROCESSING_STATUS))
                .thenReturn(501L);

        assertThatThrownBy(() -> service.submitCustomerReport(10L, customerReportRequest(20L)))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ABUSE_REPORT_ALREADY_EXISTS));

        verify(abuseReportMapper, never()).insertCustomerReport(any());
    }

    @Test
    void locksReportedInquiryWriterBeforeCustomerSupportReportInsert() {
        InquiryReportTarget target = inquiryTarget(55L, 20L, 10L, "PRDC0006");
        when(productService.getInquiryReportTarget(55L)).thenReturn(target);
        doAnswer(invocation -> {
            AbuseReport report = invocation.getArgument(0);
            report.setReportSn(502L);
            return 1;
        }).when(abuseReportMapper).insertManualReport(any(AbuseReport.class));

        service.submitCustomerSupportReport(
                10L,
                new CustomerSupportReportRequest("INQUIRY", 55L, "부적절한 문의입니다."));

        InOrder order = inOrder(memberOperationLockPort, abuseReportMapper);
        order.verify(memberOperationLockPort).lock(20L);
        order.verify(abuseReportMapper).insertManualReport(any(AbuseReport.class));
        verify(riskSignalPublisher).reportCreated(502L, 20L, false);
    }

    @Test
    void returnsActiveManualReportReferencesForPublicInquiryList() {
        List<ManualAbuseReportStatusResponse> reports = List.of(
                new ManualAbuseReportStatusResponse(501L, 55L, "ABSC0001"));
        when(abuseReportMapper.findActiveManualReportsByReferences(
                "REFC0012",
                List.of(55L, 56L),
                "ABSC0001",
                "ABSC0002"))
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
                "ABSC0001",
                "ABSC0002");
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
                "회원 20",
                "TRADE_ISSUE"))
                .thenReturn(21L);
        when(abuseReportMapper.findAdminReports(
                AbuseReportService.PROCESSED_STATUS,
                "회원 20",
                "TRADE_ISSUE",
                20L,
                20))
                .thenReturn(List.of(processedReport));

        PageResponse<AdminAbuseReportResponse> result = service.getAdminReports(
                " " + AbuseReportService.PROCESSED_STATUS + " ",
                " 회원 20 ",
                " trade_issue ",
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
                "TRADE_ISSUE",
                20L,
                20);
    }

    /** 담당자 7 · ISSUE-T7-004: 대시보드 활성 분쟁은 거래 분쟁 목록의 접수·처리중 합계입니다. */
    @Test
    void countsActiveTradeDisputesUsingAdminListFilters() {
        when(abuseReportMapper.countAdminReports(
                AbuseReportService.RECEIVED_STATUS, null, "TRADE_ISSUE")).thenReturn(3L);
        when(abuseReportMapper.countAdminReports(
                AbuseReportService.PROCESSING_STATUS, null, "TRADE_ISSUE")).thenReturn(2L);

        assertThat(service.countActiveDisputesForAdmin()).isEqualTo(5L);
    }

    @Test
    void rejectsInvalidAdminReportPageRequest() {
        assertThatThrownBy(() -> service.getAdminReports(null, null, "ALL", 0, 20))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> service.getAdminReports(null, "x".repeat(101), "ALL", 1, 20))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> service.getAdminReports(null, null, "UNKNOWN", 1, 20))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verify(abuseReportMapper, never()).countAdminReports(any(), any(), any());
        verify(abuseReportMapper, never()).findAdminReports(any(), any(), any(), anyLong(), anyInt());
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
        InOrder order = inOrder(memberOperationLockPort, abuseReportMapper);
        order.verify(memberOperationLockPort).lock(20L);
        order.verify(abuseReportMapper).insertAutomaticReport(any(AbuseReport.class));
        ArgumentCaptor<AbuseReport> reportCaptor = ArgumentCaptor.forClass(AbuseReport.class);
        verify(abuseReportMapper).insertAutomaticReport(reportCaptor.capture());
        AbuseReport report = reportCaptor.getValue();
        assertThat(report.getRiskEventSn()).isEqualTo(77L);
        assertThat(report.getReporterUserSn()).isNull();
        assertThat(report.getReportedUserSn()).isEqualTo(20L);
        assertThat(report.getReportTypeCode()).isEqualTo(AbuseReportService.PRIVACY_REPORT_TYPE);
        assertThat(report.getStatusCode()).isEqualTo(AbuseReportService.RECEIVED_STATUS);
        assertThat(report.getReferenceTypeCode()).isEqualTo("REFC0005");
        assertThat(report.getReferenceSn()).isEqualTo(31L);
        assertThat(report.getContent()).isEqualTo("민감정보 자동 탐지: EMAIL,PHONE_NUMBER");
        assertThat(report.getRegisteredBy()).isEqualTo("SYSTEM");
        assertThat(report.getUpdatedBy()).isEqualTo("SYSTEM");
        verify(referenceDataService).requireActiveCode("ABRG01", "ABRC0005");
        verify(referenceDataService).requireActiveCode("ABRG02", "ABSC0001");
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
        AbuseReport report = pendingReport(101L, AbuseReportService.PROCESSING_STATUS);
        when(abuseReportMapper.findReportByIdForUpdate(101L)).thenReturn(report);
        when(abuseReportMapper.updateDecision(
                101L,
                AbuseReportService.PROCESSING_STATUS,
                AbuseReportService.PROCESSED_STATUS,
                "위반 확인",
                7L,
                "7",
                "request-1")).thenReturn(1);

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
        assertThat(audit.referenceTypeCode()).isEqualTo("REFC0018");
        assertThat(audit.referenceSn()).isEqualTo(101L);
        assertThat(audit.relatedReferenceTypeCode()).isEqualTo("REFC0005");
        assertThat(audit.relatedReferenceSn()).isEqualTo(31L);
        assertThat(audit.reason()).isEqualTo("위반 확인");
        assertThat(audit.beforeSummary()).isEqualTo("reportSn=101,status=ABSC0002");
        assertThat(audit.afterSummary()).isEqualTo("reportSn=101,status=ABSC0003");
        assertThat(audit.requestId()).isEqualTo("request-1");
        verify(riskEventService).markProcessed(77L, "7");
        verify(notificationService).notifyReportResult(10L, 101L, "처리 완료");
    }

    @Test
    void movesReceivedReportToProcessingBeforeFinalDecision() {
        AbuseReport report = pendingReport(101L, AbuseReportService.RECEIVED_STATUS);
        when(abuseReportMapper.findReportByIdForUpdate(101L)).thenReturn(report);
        when(abuseReportMapper.updateDecision(
                101L,
                AbuseReportService.RECEIVED_STATUS,
                AbuseReportService.PROCESSING_STATUS,
                "사실관계 확인 시작",
                7L,
                "7",
                "request-processing")).thenReturn(1);

        service.decide(new AdminReportDecisionCommand(
                101L,
                AdminReportDecision.PROCESSING,
                "사실관계 확인 시작",
                "7",
                "request-processing"));

        ArgumentCaptor<AuditLogCommand> auditCaptor = ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogPort).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().actionCode()).isEqualTo("STATUS_CHANGE");
        assertThat(auditCaptor.getValue().afterSummary())
                .isEqualTo("reportSn=101,status=ABSC0002");
        verify(riskEventService, never()).markProcessed(anyLong(), any());
        verify(notificationService, never()).notifyReportResult(anyLong(), anyLong(), any());
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
                7L,
                "7",
                "request-2")).thenReturn(1);

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
                7L,
                "7",
                "request-2");
        ArgumentCaptor<AuditLogCommand> auditCaptor = ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogPort).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().actionCode()).isEqualTo("ADMIN_REJECT");
        verify(riskEventService).markProcessed(77L, "7");
        verify(notificationService).notifyReportResult(10L, 101L, "반려");
    }

    @Test
    void doesNotNotifyUserForAutomaticReportWithoutReporter() {
        AbuseReport report = pendingReport(
                101L,
                AbuseReportService.PROCESSING_STATUS,
                null);
        when(abuseReportMapper.findReportByIdForUpdate(101L)).thenReturn(report);
        when(abuseReportMapper.updateDecision(
                101L,
                AbuseReportService.PROCESSING_STATUS,
                AbuseReportService.PROCESSED_STATUS,
                "자동 탐지 확인",
                7L,
                "7",
                "request-system-1")).thenReturn(1);

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
                pendingReport(101L, AbuseReportService.PROCESSING_STATUS));
        AtomicBoolean updated = new AtomicBoolean();
        when(abuseReportMapper.updateDecision(
                eq(101L),
                eq(AbuseReportService.PROCESSING_STATUS),
                any(),
                any(),
                eq(7L),
                eq("7"),
                any())).thenAnswer(invocation -> updated.compareAndSet(false, true) ? 1 : 0);

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
                20L,
                Set.of(SensitiveDataType.PHONE_NUMBER, SensitiveDataType.EMAIL),
                "20");
    }

    private CustomerAbuseReportRequest customerReportRequest(Long reportedUserSn) {
        return new CustomerAbuseReportRequest(
                AbuseReportService.FALSE_INFORMATION_FRAUD_REPORT_TYPE,
                reportedUserSn,
                reportedUserSn == null ? null : "REFC0001",
                reportedUserSn,
                null,
                "report title",
                "report content",
                List.of());
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
                AbuseReportService.FALSE_INFORMATION_FRAUD_REPORT_TYPE,
                statusCode,
                "[허위 정보] 테스트 경매 상품",
                "테스트 경매 상품",
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
