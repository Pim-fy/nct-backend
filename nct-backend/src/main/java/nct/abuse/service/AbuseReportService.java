package nct.abuse.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;
import nct.abuse.domain.AbuseReport;
import nct.abuse.dto.AdminAbuseReportResponse;
import nct.abuse.dto.CustomerAbuseReportRequest;
import nct.abuse.dto.CustomerSupportReportRequest;
import nct.abuse.dto.ManualAbuseReportRequest;
import nct.abuse.dto.ManualAbuseReportResponse;
import nct.abuse.dto.ManualAbuseReportStatusResponse;
import nct.abuse.dto.MyAbuseReportResponse;
import nct.file.service.FileStorageService;
import nct.global.security.port.AuthMemberPort;
import nct.global.response.PageResponse;
import nct.abuse.mapper.AbuseReportMapper;
import nct.abuse.port.ActiveAbuseReportReferenceReader;
import nct.abuse.port.ActiveReportedUserReader;
import nct.abuse.port.AdminActiveDisputeCountReader;
import nct.abuse.port.TradeIncidentReportCommand;
import nct.abuse.port.TradeIncidentReportPort;
import nct.auction.port.AuctionReferenceTitleReader;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.member.port.MemberOperationLockPort;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.operation.port.AdminReportDecisionCommand;
import nct.ops.operation.port.AdminReportDecisionPort;
import nct.ops.reference.service.ReferenceDataService;
import nct.ops.risk.service.RiskEventService;
import nct.ops.security.port.SensitiveDetectionReportCommand;
import nct.ops.security.port.SensitiveDetectionReportPort;
import nct.ops.security.port.SensitiveDetectionReportResult;
import nct.product.dto.InquiryReportTarget;
import nct.product.service.ProductService;
import nct.servicerequest.port.ServiceRequestQuoteReader;
import nct.trade.dto.AdminReportTradeReference;
import nct.trade.port.AdminReportTradeReferenceReader;

@Service
@RequiredArgsConstructor
public class AbuseReportService implements
        SensitiveDetectionReportPort,
        AdminReportDecisionPort,
        ActiveAbuseReportReferenceReader,
        ActiveReportedUserReader,
        AdminActiveDisputeCountReader,
        TradeIncidentReportPort {

    static final String FALSE_INFORMATION_FRAUD_REPORT_TYPE = "ABRC0001";
    static final String EXTERNAL_CONTACT_PAYMENT_REPORT_TYPE = "ABRC0002";
    static final String ABUSE_HARASSMENT_REPORT_TYPE = "ABRC0003";
    static final String PROHIBITED_ILLEGAL_REPORT_TYPE = "ABRC0004";
    static final String PRIVACY_REPORT_TYPE = "ABRC0005";
    static final String SPAM_ADVERTISEMENT_REPORT_TYPE = "ABRC0006";
    static final String OTHER_REPORT_TYPE = "ABRC0007";
    static final String TRADE_NO_SHOW_REPORT_TYPE = "ABRC0008";
    static final String TRADE_DELIVERY_REPORT_TYPE = "ABRC0009";
    static final String TRADE_SERVICE_REPORT_TYPE = "ABRC0010";
    static final String TRADE_PAYMENT_REPORT_TYPE = "ABRC0011";
    static final String RECEIVED_STATUS = "ABSC0001";
    static final String PROCESSING_STATUS = "ABSC0002";
    static final String PROCESSED_STATUS = "ABSC0003";
    static final String REJECTED_STATUS = "ABSC0004";
    static final String PRODUCT_COMMENT_REFERENCE_TYPE = "REFC0012";
    static final String AUCTION_REFERENCE_TYPE = "REFC0003";
    static final String TRADE_REFERENCE_TYPE = "REFC0005";
    static final String SERVICE_REQUEST_REFERENCE_TYPE = "REFC0007";
    static final String SYSTEM_SETTING_REFERENCE_TYPE = "REFC0010";
    static final String NOTICE_REFERENCE_TYPE = "REFC0011";
    static final String CUSTOMER_INQUIRY_REFERENCE_TYPE = "REFC0013";
    private static final String AUCTION_DETAIL_TYPE = "AUCTION";
    private static final String SERVICE_REQUEST_DETAIL_TYPE = "SERVICE_REQUEST";
    private static final String SERVICE_TRADE_DETAIL_TYPE = "SERVICE_TRADE";
    private static final String SYSTEM_SETTING_DETAIL_TYPE = "SYSTEM_SETTING";
    private static final String NOTICE_DETAIL_TYPE = "NOTICE";
    private static final String CUSTOMER_INQUIRY_DETAIL_TYPE = "CUSTOMER_INQUIRY";

    private static final String REPORT_TYPE_GROUP = "ABRG01";
    private static final String REPORT_STATUS_GROUP = "ABRG02";
    private static final String REFERENCE_TYPE_GROUP = "REFG01";
    private static final String SYSTEM_ACTOR = "SYSTEM";
    private static final String BUYER_INQUIRY_TYPE = "PRDC0006";
    private static final String DEFAULT_MANUAL_REPORT_CONTENT = "상품 댓글·문의 신고";
    private static final int MAX_PROCESS_REASON_LENGTH = 4000;
    private static final int MAX_REQUEST_ID_LENGTH = 100;
    private static final int MAX_PUBLIC_REFERENCE_LOOKUP_SIZE = 100;
    private static final int MAX_ADMIN_REPORT_PAGE_SIZE = 50;
    private static final int MAX_ADMIN_REPORT_KEYWORD_LENGTH = 100;
    private static final int MAX_REPORT_FILES = 5;
    private static final Set<String> TRADE_INCIDENT_REPORT_TYPES = Set.of(
            TRADE_NO_SHOW_REPORT_TYPE,
            TRADE_DELIVERY_REPORT_TYPE,
            TRADE_SERVICE_REPORT_TYPE,
            TRADE_PAYMENT_REPORT_TYPE);
    private static final Set<String> CUSTOMER_REPORT_TYPES = Set.of(
            FALSE_INFORMATION_FRAUD_REPORT_TYPE,
            EXTERNAL_CONTACT_PAYMENT_REPORT_TYPE,
            ABUSE_HARASSMENT_REPORT_TYPE,
            PROHIBITED_ILLEGAL_REPORT_TYPE,
            PRIVACY_REPORT_TYPE,
            SPAM_ADVERTISEMENT_REPORT_TYPE,
            OTHER_REPORT_TYPE);

    private final AbuseReportMapper abuseReportMapper;
    private final AuctionReferenceTitleReader auctionReferenceTitleReader;
    private final ServiceRequestQuoteReader serviceRequestQuoteReader;
    private final AdminReportTradeReferenceReader tradeReferenceReader;
    private final ReferenceDataService referenceDataService;
    private final AbuseReportReferenceValidationService referenceValidationService;
    private final AuditLogPort auditLogPort;
    private final NotificationService notificationService;
    private final ObjectProvider<ProductService> productServiceProvider;
    private final AuthMemberPort authMemberPort;
    private final MemberOperationLockPort memberOperationLockPort;
    private final FileStorageService fileStorageService;
    private final RiskEventService riskEventService;
    private final ReportTargetHoldService reportTargetHoldService;
    private final ConcurrentMap<CustomerReportKey, LockEntry> customerReportLocks = new ConcurrentHashMap<>();

    /** 거래 도메인이 당사자·거래·첨부를 검증한 뒤 생성하는 신고 상위 사건입니다. */
    @Override
    @Transactional
    public Long create(TradeIncidentReportCommand command) {
        if (command == null
                || command.tradeSn() == null || command.tradeSn() <= 0
                || command.reporterUserSn() == null || command.reporterUserSn() <= 0
                || command.reportedUserSn() == null || command.reportedUserSn() <= 0
                || command.reporterUserSn().equals(command.reportedUserSn())
                || command.reportTypeCode() == null || command.reportTypeCode().isBlank()
                || command.content() == null || command.content().isBlank()
                || command.content().trim().length() > 4000
                || command.previousTradeStatusCode() == null
                || command.previousTradeStatusCode().isBlank()
                || (command.remainingAutoCompleteSeconds() != null
                    && command.remainingAutoCompleteSeconds() < 0)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        List<Long> fileSns = command.fileSns() == null ? List.of() : List.copyOf(command.fileSns());
        if (fileSns.size() > MAX_REPORT_FILES
                || new LinkedHashSet<>(fileSns).size() != fileSns.size()
                || fileSns.stream().anyMatch(fileSn -> fileSn == null || fileSn <= 0)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        String reportTypeCode = command.reportTypeCode().trim();
        if (!TRADE_INCIDENT_REPORT_TYPES.contains(reportTypeCode)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        referenceDataService.requireActiveCode(REPORT_TYPE_GROUP, reportTypeCode);
        referenceDataService.requireActiveCode(REPORT_STATUS_GROUP, RECEIVED_STATUS);
        referenceDataService.requireActiveCode(REFERENCE_TYPE_GROUP, TRADE_REFERENCE_TYPE);
        memberOperationLockPort.lock(command.reportedUserSn());

        String actorId = String.valueOf(command.reporterUserSn());
        AbuseReport report = AbuseReport.builder()
                .reporterUserSn(command.reporterUserSn())
                .reportedUserSn(command.reportedUserSn())
                .reportTypeCode(reportTypeCode)
                .statusCode(RECEIVED_STATUS)
                .referenceTypeCode(TRADE_REFERENCE_TYPE)
                .referenceSn(command.tradeSn())
                .title("거래 문제 신고 #" + command.tradeSn())
                .targetName("거래 #" + command.tradeSn())
                .content(command.content().trim())
                .registeredBy(actorId)
                .updatedBy(actorId)
                .build();
        if (abuseReportMapper.insertCustomerReport(report) != 1 || report.getReportSn() == null) {
            throw new CustomException(ErrorCode.DATABASE_ERROR);
        }
        for (int index = 0; index < fileSns.size(); index++) {
            if (abuseReportMapper.insertReportFile(
                    report.getReportSn(), fileSns.get(index), index, actorId) != 1) {
                throw new CustomException(ErrorCode.DATABASE_ERROR);
            }
        }
        if (abuseReportMapper.insertTradeContext(
                report.getReportSn(),
                command.tradeSn(),
                command.previousTradeStatusCode().trim(),
                command.remainingAutoCompleteSeconds(),
                command.settlementHoldApplied(),
                command.chatClosed(),
                actorId) != 1) {
            throw new CustomException(ErrorCode.DATABASE_ERROR);
        }
        return report.getReportSn();
    }

    /** F-COM-018: 로그인 사용자가 고객센터형 신고를 접수한다. */
    @Transactional
    public ManualAbuseReportResponse submitCustomerReport(
            Long reporterUserSn,
            CustomerAbuseReportRequest request) {
        if (reporterUserSn == null
                || reporterUserSn <= 0
                || request == null
                || request.reportTypeCode() == null
                || request.reportTypeCode().isBlank()
                || request.title() == null
                || request.title().isBlank()
                || request.content() == null
                || request.content().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String reportTypeCode = request.reportTypeCode().trim();
        validateCustomerReportType(reportTypeCode);
        String referenceTypeCode = trimToNull(request.referenceTypeCode());
        validateCustomerReferenceShape(
                referenceTypeCode,
                request.referenceSn(),
                request.reportedUserSn());
        validateCustomerReportedUser(reporterUserSn, request.reportedUserSn());
        referenceDataService.requireActiveCode(REPORT_STATUS_GROUP, RECEIVED_STATUS);
        if (referenceTypeCode != null) {
            referenceDataService.requireActiveCode(REFERENCE_TYPE_GROUP, referenceTypeCode);
        }
        String verifiedTargetName = referenceValidationService.requireValid(
                reporterUserSn,
                request.reportedUserSn(),
                referenceTypeCode,
                request.referenceSn());

        String actorId = String.valueOf(reporterUserSn);
        List<Long> fileSns = validateReportFiles(request.fileSns(), reporterUserSn);
        CustomerReportKey key = new CustomerReportKey(
                reporterUserSn,
                request.reportedUserSn(),
                reportTypeCode,
                referenceTypeCode,
                request.referenceSn());
        LockEntry lockEntry = acquireCustomerReportLock(key);
        boolean releaseInFinally = registerCustomerReportLockRelease(key, lockEntry);

        try {
            memberOperationLockPort.lock(request.reportedUserSn());
            Long existingReportSn = abuseReportMapper.findActiveCustomerReportId(
                    reporterUserSn,
                    request.reportedUserSn(),
                    reportTypeCode,
                    referenceTypeCode,
                    request.referenceSn(),
                    RECEIVED_STATUS,
                    PROCESSING_STATUS);
            if (existingReportSn != null) {
                throw new CustomException(ErrorCode.ABUSE_REPORT_ALREADY_EXISTS);
            }

            AbuseReport report = AbuseReport.builder()
                    .riskEventSn(null)
                    .reporterUserSn(reporterUserSn)
                    .reportedUserSn(request.reportedUserSn())
                    .reportTypeCode(reportTypeCode)
                    .statusCode(RECEIVED_STATUS)
                    .referenceTypeCode(referenceTypeCode)
                    .referenceSn(request.referenceSn())
                    .title(request.title().trim())
                    .targetName(verifiedTargetName == null
                            ? trimToNull(request.targetName())
                            : verifiedTargetName)
                    .content(request.content().trim())
                    .registeredBy(actorId)
                    .updatedBy(actorId)
                    .build();

            int inserted;
            try {
                inserted = abuseReportMapper.insertCustomerReport(report);
            } catch (DuplicateKeyException duplicate) {
                throw new CustomException(ErrorCode.ABUSE_REPORT_ALREADY_EXISTS);
            }
            if (inserted != 1 || report.getReportSn() == null) {
                throw new CustomException(ErrorCode.DATABASE_ERROR);
            }
            for (int index = 0; index < fileSns.size(); index++) {
                if (abuseReportMapper.insertReportFile(
                        report.getReportSn(), fileSns.get(index), index, actorId) != 1) {
                    throw new CustomException(ErrorCode.DATABASE_ERROR);
                }
            }
            if (referenceTypeCode != null && request.referenceSn() != null) {
                reportTargetHoldService.pause(
                        report.getReportSn(),
                        referenceTypeCode,
                        request.referenceSn(),
                        actorId);
            }
            return new ManualAbuseReportResponse(report.getReportSn());
        } finally {
            if (releaseInFinally) {
                releaseCustomerReportLock(key, lockEntry);
            }
        }
    }

    /** F-COM-018: 내 신고 목록을 페이지 단위로 조회한다. */
    @Transactional(readOnly = true)
    public PageResponse<MyAbuseReportResponse> getMyReports(
            Long reporterUserSn,
            String statusCode,
            int page,
            int size) {
        if (reporterUserSn == null || reporterUserSn <= 0 || page < 1 || size < 1 || size > 50) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String normalizedStatus = (statusCode == null || statusCode.isBlank()) ? null : statusCode.trim();
        int offset = (page - 1) * size;
        List<MyAbuseReportResponse> content = abuseReportMapper.findMyReports(
                reporterUserSn, normalizedStatus, offset, size);
        enrichMyAuctionTargetNames(content);
        int total = abuseReportMapper.countMyReports(reporterUserSn, normalizedStatus);
        return PageResponse.<MyAbuseReportResponse>builder()
                .content(content)
                .totalCount(total)
                .page(page)
                .size(size)
                .hasNext(offset + content.size() < total)
                .build();
    }

    /** F-COM-018: 내 신고 단건 상세를 조회한다. */
    @Transactional(readOnly = true)
    public MyAbuseReportResponse getMyReportDetail(Long reporterUserSn, Long reportSn) {
        if (reporterUserSn == null || reporterUserSn <= 0
                || reportSn == null || reportSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        MyAbuseReportResponse report = abuseReportMapper.findMyReportById(reportSn, reporterUserSn);
        if (report == null) {
            throw new CustomException(ErrorCode.ABUSE_REPORT_NOT_FOUND);
        }
        enrichMyAuctionTargetNames(List.of(report));
        attachReportFiles(report);
        return report;
    }

    /**
     * 신현석(담당자2) 연동용: ProductDetailSellerPage 구매자 문의 신고 버튼 접수.
     * targetType="INQUIRY" → REFC0012(상품 댓글·문의) 매핑.
     */
    @Transactional
    public void submitCustomerSupportReport(
            Long reporterUserSn,
            CustomerSupportReportRequest request) {
        if (reporterUserSn == null || reporterUserSn <= 0 || request == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!"INQUIRY".equalsIgnoreCase(request.targetType())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        InquiryReportTarget target = productServiceProvider.getObject()
                .getInquiryReportTarget(request.targetSn());
        if (target == null
                || !request.targetSn().equals(target.getPrdCmtSn())
                || !BUYER_INQUIRY_TYPE.equals(target.getPrdCmtTypeCd())
                || target.getWriterUsrSn() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (reporterUserSn.equals(target.getWriterUsrSn())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!reporterUserSn.equals(target.getSellerUsrSn())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        validateReferenceCodes(OTHER_REPORT_TYPE, PRODUCT_COMMENT_REFERENCE_TYPE);
        memberOperationLockPort.lock(target.getWriterUsrSn());
        Long existingReport = abuseReportMapper.findManualReportId(
                reporterUserSn, PRODUCT_COMMENT_REFERENCE_TYPE, request.targetSn());
        if (existingReport != null) {
            throw new CustomException(ErrorCode.ABUSE_REPORT_ALREADY_EXISTS);
        }
        String actorId = String.valueOf(reporterUserSn);
        AbuseReport report = AbuseReport.builder()
                .riskEventSn(null)
                .reporterUserSn(reporterUserSn)
                .reportedUserSn(target.getWriterUsrSn())
                .reportTypeCode(OTHER_REPORT_TYPE)
                .statusCode(RECEIVED_STATUS)
                .referenceTypeCode(PRODUCT_COMMENT_REFERENCE_TYPE)
                .referenceSn(request.targetSn())
                .content(request.reportContent().trim())
                .registeredBy(actorId)
                .updatedBy(actorId)
                .build();

        int inserted = abuseReportMapper.insertManualReport(report);
        if (inserted != 1 || report.getReportSn() == null) {
            throw new CustomException(ErrorCode.DATABASE_ERROR);
        }
    }

    /** 판매자가 자기 상품에 등록된 구매자 문의를 수동 신고한다. */
    @Transactional
    public ManualAbuseReportResponse requestManualReport(
            Long reporterUserSn,
            ManualAbuseReportRequest request) {
        ManualReportValues values = validateManualReport(reporterUserSn, request);
        validateReferenceCodes(OTHER_REPORT_TYPE, values.referenceTypeCode());
        memberOperationLockPort.lock(values.reportedUserSn());
        rejectDuplicateManualReport(values);

        String actorId = String.valueOf(values.reporterUserSn());
        AbuseReport report = AbuseReport.builder()
                .riskEventSn(null)
                .reporterUserSn(values.reporterUserSn())
                .reportedUserSn(values.reportedUserSn())
                .reportTypeCode(OTHER_REPORT_TYPE)
                .statusCode(RECEIVED_STATUS)
                .referenceTypeCode(values.referenceTypeCode())
                .referenceSn(values.referenceSn())
                .content(values.content())
                .registeredBy(actorId)
                .updatedBy(actorId)
                .build();

        int inserted;
        try {
            inserted = abuseReportMapper.insertManualReport(report);
        } catch (DuplicateKeyException duplicate) {
            throw new CustomException(ErrorCode.ABUSE_REPORT_ALREADY_EXISTS);
        }
        if (inserted != 1 || report.getReportSn() == null) {
            throw new CustomException(ErrorCode.DATABASE_ERROR);
        }
        return new ManualAbuseReportResponse(report.getReportSn());
    }

    /** 로그인 사용자가 이미 신고한 참조 목록을 조회한다. */
    @Transactional(readOnly = true)
    public List<ManualAbuseReportStatusResponse> getMyManualReportReferences(
            Long reporterUserSn,
            String referenceTypeCode) {
        String normalizedReferenceType = validateManualReferenceType(
                reporterUserSn,
                referenceTypeCode);
        referenceDataService.requireActiveCode(
                REFERENCE_TYPE_GROUP,
                normalizedReferenceType);
        return abuseReportMapper.findManualReportsByReporterAndReferenceType(
                reporterUserSn,
                normalizedReferenceType);
    }

    /** 공개 문의 목록에서 접수·처리중인 신고 표시를 위해 참조 상태만 조회한다. */
    @Transactional(readOnly = true)
    public List<ManualAbuseReportStatusResponse> getActiveManualReportReferences(
            String referenceTypeCode,
            List<Long> referenceSns) {
        String normalizedReferenceType = normalizeManualReferenceType(referenceTypeCode);
        if (referenceSns == null
                || referenceSns.isEmpty()
                || referenceSns.size() > MAX_PUBLIC_REFERENCE_LOOKUP_SIZE
                || referenceSns.stream().anyMatch(referenceSn -> referenceSn == null || referenceSn <= 0)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        List<Long> normalizedReferenceSns = referenceSns.stream()
                .distinct()
                .toList();
        referenceDataService.requireActiveCode(
                REFERENCE_TYPE_GROUP,
                normalizedReferenceType);
        return abuseReportMapper.findActiveManualReportsByReferences(
                normalizedReferenceType,
                normalizedReferenceSns,
                RECEIVED_STATUS,
                PROCESSING_STATUS);
    }

    /** 위험 이벤트 하나당 SYSTEM 자동 신고를 정확히 한 건 생성하거나 기존 신고를 재사용한다. */
    @Override
    @Transactional
    public SensitiveDetectionReportResult requestReport(SensitiveDetectionReportCommand command) {
        validateAutomaticReport(command);
        validateReferenceCodes(PRIVACY_REPORT_TYPE, command.referenceTypeCode());
        memberOperationLockPort.lock(command.reportedUserSn());

        AbuseReport report = AbuseReport.builder()
                .riskEventSn(command.riskEventSn())
                .reporterUserSn(null)
                .reportedUserSn(command.reportedUserSn())
                .reportTypeCode(PRIVACY_REPORT_TYPE)
                .statusCode(RECEIVED_STATUS)
                .referenceTypeCode(trimToNull(command.referenceTypeCode()))
                .referenceSn(command.referenceSn())
                .content(automaticReportContent(command))
                .registeredBy(SYSTEM_ACTOR)
                .updatedBy(SYSTEM_ACTOR)
                .build();

        try {
            int inserted = abuseReportMapper.insertAutomaticReport(report);
            if (inserted != 1 || report.getReportSn() == null) {
                throw new CustomException(ErrorCode.DATABASE_ERROR);
            }
            return new SensitiveDetectionReportResult(
                    SensitiveDetectionReportResult.Status.CREATED,
                    report.getReportSn());
        } catch (DuplicateKeyException duplicate) {
            // UK_ABUSE_REPORT_RISK_EVENT가 서버 간 동시 호출도 한 건으로 수렴시킨다.
            Long existingReportSn = abuseReportMapper.findReportIdByRiskEventIdForUpdate(
                    command.riskEventSn());
            if (existingReportSn == null) {
                throw new CustomException(ErrorCode.DATABASE_ERROR);
            }
            return new SensitiveDetectionReportResult(
                    SensitiveDetectionReportResult.Status.REUSED,
                    existingReportSn);
        }
    }

    /** 관리자의 완료·반려 결정을 처리 사유 및 감사로그와 같은 트랜잭션에 기록한다. */
    @Override
    @Transactional
    public void decide(AdminReportDecisionCommand command) {
        DecisionValues values = validateDecision(command);
        AbuseReport report = abuseReportMapper.findReportByIdForUpdate(command.reportSn());
        if (report == null) {
            throw new CustomException(ErrorCode.ABUSE_REPORT_NOT_FOUND);
        }
        if (!values.expectedStatusCode().equals(report.getStatusCode())) {
            if (values.requestId().equals(report.getProcessRequestId())
                    && values.newStatusCode().equals(report.getStatusCode())) {
                return;
            }
            throw new CustomException(ErrorCode.ABUSE_REPORT_ALREADY_PROCESSED);
        }

        referenceDataService.requireActiveCode(REPORT_STATUS_GROUP, values.newStatusCode());
        int updated = abuseReportMapper.updateDecision(
                report.getReportSn(),
                report.getStatusCode(),
                values.newStatusCode(),
                values.reason(),
                Long.valueOf(values.adminId()),
                values.adminId(),
                values.requestId());
        if (updated != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "신고 상태가 이미 변경되었습니다.");
        }

        if (values.finalDecision() && report.getRiskEventSn() != null) {
            riskEventService.markProcessed(report.getRiskEventSn(), values.adminId());
        }

        auditLogPort.record(new AuditLogCommand(
                values.auditAction(),
                values.adminId(),
                RefType.ABUSE_REPORT.getCode(),
                report.getReportSn(),
                values.reason(),
                statusSummary(report.getReportSn(), report.getStatusCode()),
                statusSummary(report.getReportSn(), values.newStatusCode()),
                values.requestId(),
                report.getReferenceTypeCode(),
                report.getReferenceSn()));

        // 담당자 7 · F-OPS-007: 일반 신고에만 처리 결과를 알리고,
        // 신고자가 없는 SYSTEM 자동 탐지 신고에는 사용자 알림을 만들지 않는다.
        if (values.finalDecision()
                && report.getReporterUserSn() != null
                && report.getReporterUserSn() > 0) {
            notificationService.notifyReportResult(
                    report.getReporterUserSn(),
                    report.getReportSn(),
                    decisionResult(values.newStatusCode()));
        }
    }

    /** 접수·처리중 상태의 신고를 자동·일반 신고 구분 없이 오래된 순서로 조회한다. */
    @Transactional(readOnly = true)
    public List<AdminAbuseReportResponse> getPendingReports() {
        List<AdminAbuseReportResponse> reports =
                abuseReportMapper.findPendingReports(RECEIVED_STATUS, PROCESSING_STATUS);
        enrichAdminReferenceSummaries(reports);
        return reports;
    }

    /** 담당자 7 · F-OPS-010: 거래 분쟁 목록의 접수·처리중 합계를 그대로 대시보드에 제공합니다. */
    @Override
    @Transactional(readOnly = true)
    public long countActiveDisputesForAdmin() {
        return abuseReportMapper.countAdminReports(RECEIVED_STATUS, null, "TRADE_ISSUE")
                + abuseReportMapper.countAdminReports(PROCESSING_STATUS, null, "TRADE_ISSUE");
    }

    /** 담당자 7 · F-OPS-007: 처리 전후 신고를 상태·검색 조건으로 페이지 조회한다. */
    @Transactional(readOnly = true)
    public PageResponse<AdminAbuseReportResponse> getAdminReports(
            String statusCode,
            String keyword,
            String caseType,
            int page,
            int size) {
        if (page < 1 || size < 1 || size > MAX_ADMIN_REPORT_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        String normalizedStatus = trimToNull(statusCode);
        String normalizedKeyword = trimToNull(keyword);
        String normalizedCaseType = trimToNull(caseType);
        normalizedCaseType = normalizedCaseType == null ? "ALL" : normalizedCaseType.toUpperCase();
        if (!Set.of("ALL", "GENERAL", "TRADE_ISSUE").contains(normalizedCaseType)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (normalizedKeyword != null && normalizedKeyword.length() > MAX_ADMIN_REPORT_KEYWORD_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (normalizedStatus != null) {
            referenceDataService.requireActiveCode(REPORT_STATUS_GROUP, normalizedStatus);
        }

        long offset = (long) (page - 1) * size;
        long total = abuseReportMapper.countAdminReports(
                normalizedStatus,
                normalizedKeyword,
                normalizedCaseType);
        List<AdminAbuseReportResponse> content = total == 0 || offset >= total
                ? List.of()
                : abuseReportMapper.findAdminReports(
                        normalizedStatus,
                        normalizedKeyword,
                        normalizedCaseType,
                        offset,
                        size);
        enrichAdminReferenceSummaries(content);
        return PageResponse.<AdminAbuseReportResponse>builder()
                .content(content)
                .totalCount(total)
                .page(page)
                .size(size)
                .hasNext(offset + content.size() < total)
                .build();
    }

    /** 관리자 화면에서 처리 전후의 신고 상세를 조회한다. */
    @Transactional(readOnly = true)
    public AdminAbuseReportResponse getReportDetail(Long reportSn) {
        if (reportSn == null || reportSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        AdminAbuseReportResponse report = abuseReportMapper.findReportDetailById(reportSn);
        if (report == null) {
            throw new CustomException(ErrorCode.ABUSE_REPORT_NOT_FOUND);
        }
        enrichAdminReferenceSummaries(List.of(report));
        report.setFiles(abuseReportMapper.findReportFiles(reportSn));
        return report;
    }

    private void enrichMyAuctionTargetNames(List<MyAbuseReportResponse> reports) {
        if (reports == null || reports.isEmpty()) {
            return;
        }
        List<Long> auctionIds = reports.stream()
                .filter(report -> shouldResolveAuctionTarget(
                        report.getReferenceTypeCode(), report.getReferenceSn(), report.getTargetName()))
                .map(MyAbuseReportResponse::getReferenceSn)
                .distinct()
                .toList();
        if (auctionIds.isEmpty()) {
            return;
        }
        Map<Long, String> titles = auctionReferenceTitleReader.findTitles(auctionIds);
        reports.forEach(report -> applyAuctionTitle(
                report.getReferenceTypeCode(),
                report.getReferenceSn(),
                report.getTargetName(),
                report::setTargetName,
                titles));
    }

    private void enrichAdminAuctionTargetNames(List<AdminAbuseReportResponse> reports) {
        if (reports == null || reports.isEmpty()) {
            return;
        }
        List<Long> auctionIds = reports.stream()
                .filter(report -> shouldResolveAuctionTarget(
                        report.getReferenceTypeCode(), report.getReferenceSn(), report.getTargetName()))
                .map(AdminAbuseReportResponse::getReferenceSn)
                .distinct()
                .toList();
        if (auctionIds.isEmpty()) {
            return;
        }
        Map<Long, String> titles = auctionReferenceTitleReader.findTitles(auctionIds);
        reports.forEach(report -> applyAuctionTitle(
                report.getReferenceTypeCode(),
                report.getReferenceSn(),
                report.getTargetName(),
                report::setTargetName,
                titles));
    }

    /** 담당자 7 · F-OPS-007: 신고 참조 번호를 실제 제목과 관리자 상세 이동 대상으로 조립합니다. */
    private void enrichAdminReferenceSummaries(List<AdminAbuseReportResponse> reports) {
        if (reports == null || reports.isEmpty()) {
            return;
        }

        enrichAdminTradeReferences(reports);
        enrichAdminAuctionTargetNames(reports);

        List<Long> serviceRequestIds = reports.stream()
                .map(this::serviceRequestIdForReference)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> serviceRequestTitles = serviceRequestIds.isEmpty()
                ? Map.of()
                : serviceRequestQuoteReader.findTitles(serviceRequestIds);

        List<Long> productIds = reports.stream()
                .filter(report -> TRADE_REFERENCE_TYPE.equals(report.getReferenceTypeCode()))
                .filter(report -> report.getServiceRequestSn() == null)
                .map(AdminAbuseReportResponse::getProductSn)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Long> auctionIdsByProduct = productIds.isEmpty()
                ? Map.of()
                : auctionReferenceTitleReader.findAuctionIdsByProductIds(productIds);
        Map<Long, String> auctionTitles = auctionIdsByProduct.isEmpty()
                ? Map.of()
                : auctionReferenceTitleReader.findTitles(
                        auctionIdsByProduct.values().stream().distinct().toList());

        reports.forEach(report -> applyAdminReferenceSummary(
                report,
                serviceRequestTitles,
                auctionIdsByProduct,
                auctionTitles));
    }

    private void enrichAdminTradeReferences(List<AdminAbuseReportResponse> reports) {
        List<Long> unresolvedTradeSns = reports.stream()
                .filter(report -> report != null
                        && TRADE_REFERENCE_TYPE.equals(report.getReferenceTypeCode()))
                .filter(report -> report.getTradeSn() == null
                        || (report.getProductSn() == null && report.getServiceRequestSn() == null))
                .map(AdminAbuseReportResponse::getReferenceSn)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (unresolvedTradeSns.isEmpty()) {
            return;
        }

        Map<Long, AdminReportTradeReference> references =
                tradeReferenceReader.findByTradeSns(unresolvedTradeSns);
        reports.forEach(report -> applyTradeReference(report, references));
    }

    private void applyTradeReference(
            AdminAbuseReportResponse report,
            Map<Long, AdminReportTradeReference> references) {
        if (report == null || !TRADE_REFERENCE_TYPE.equals(report.getReferenceTypeCode())) {
            return;
        }
        Long referenceSn = report.getReferenceSn();
        if (referenceSn == null) {
            return;
        }
        AdminReportTradeReference reference = references.get(referenceSn);
        if (reference == null) {
            return;
        }
        if (report.getTradeSn() == null) {
            report.setTradeSn(reference.getTradeSn());
        }
        if (report.getProductSn() == null) {
            report.setProductSn(reference.getProductSn());
        }
        if (report.getServiceRequestSn() == null) {
            report.setServiceRequestSn(reference.getServiceRequestSn());
        }
    }

    private Long serviceRequestIdForReference(AdminAbuseReportResponse report) {
        if (report == null) {
            return null;
        }
        if (SERVICE_REQUEST_REFERENCE_TYPE.equals(report.getReferenceTypeCode())) {
            return report.getReferenceSn();
        }
        if (TRADE_REFERENCE_TYPE.equals(report.getReferenceTypeCode())) {
            return report.getServiceRequestSn();
        }
        return null;
    }

    private void applyAdminReferenceSummary(
            AdminAbuseReportResponse report,
            Map<Long, String> serviceRequestTitles,
            Map<Long, Long> auctionIdsByProduct,
            Map<Long, String> auctionTitles) {
        report.setReferenceTitle(trimToNull(report.getTargetName()));

        String referenceTypeCode = report.getReferenceTypeCode();
        if (AUCTION_REFERENCE_TYPE.equals(referenceTypeCode)) {
            report.setReferenceDetailType(AUCTION_DETAIL_TYPE);
            report.setReferenceDetailSn(report.getReferenceSn());
            return;
        }
        if (SERVICE_REQUEST_REFERENCE_TYPE.equals(referenceTypeCode)) {
            report.setReferenceTitle(firstNonBlank(
                    serviceRequestTitles.get(report.getReferenceSn()),
                    report.getReferenceTitle()));
            report.setReferenceDetailType(SERVICE_REQUEST_DETAIL_TYPE);
            report.setReferenceDetailSn(report.getReferenceSn());
            return;
        }
        if (TRADE_REFERENCE_TYPE.equals(referenceTypeCode)) {
            applyTradeReferenceSummary(
                    report,
                    serviceRequestTitles,
                    auctionIdsByProduct,
                    auctionTitles);
            return;
        }
        if (SYSTEM_SETTING_REFERENCE_TYPE.equals(referenceTypeCode)) {
            report.setReferenceTitle("시스템 설정");
            report.setReferenceDetailType(SYSTEM_SETTING_DETAIL_TYPE);
            report.setReferenceDetailSn(report.getReferenceSn());
            return;
        }
        if (NOTICE_REFERENCE_TYPE.equals(referenceTypeCode)) {
            report.setReferenceDetailType(NOTICE_DETAIL_TYPE);
            report.setReferenceDetailSn(report.getReferenceSn());
            return;
        }
        if (CUSTOMER_INQUIRY_REFERENCE_TYPE.equals(referenceTypeCode)) {
            report.setReferenceDetailType(CUSTOMER_INQUIRY_DETAIL_TYPE);
            report.setReferenceDetailSn(report.getReferenceSn());
        }
    }

    private void applyTradeReferenceSummary(
            AdminAbuseReportResponse report,
            Map<Long, String> serviceRequestTitles,
            Map<Long, Long> auctionIdsByProduct,
            Map<Long, String> auctionTitles) {
        if (report.getServiceRequestSn() != null) {
            report.setReferenceTitle(firstNonBlank(
                    serviceRequestTitles.get(report.getServiceRequestSn()),
                    report.getReferenceTitle()));
            report.setReferenceDetailType(SERVICE_TRADE_DETAIL_TYPE);
            report.setReferenceDetailSn(firstNonNull(report.getTradeSn(), report.getReferenceSn()));
            return;
        }

        Long productSn = report.getProductSn();
        if (productSn == null) {
            return;
        }
        Long auctionId = auctionIdsByProduct.get(productSn);
        if (auctionId == null) {
            return;
        }
        report.setReferenceTitle(firstNonBlank(auctionTitles.get(auctionId), report.getReferenceTitle()));
        report.setReferenceDetailType(AUCTION_DETAIL_TYPE);
        report.setReferenceDetailSn(auctionId);
    }

    private String firstNonBlank(String preferred, String fallback) {
        String normalized = trimToNull(preferred);
        return normalized == null ? trimToNull(fallback) : normalized;
    }

    private Long firstNonNull(Long preferred, Long fallback) {
        return preferred == null ? fallback : preferred;
    }

    private boolean shouldResolveAuctionTarget(
            String referenceTypeCode,
            Long referenceSn,
            String targetName) {
        if (!AUCTION_REFERENCE_TYPE.equals(referenceTypeCode) || referenceSn == null) {
            return false;
        }
        String normalizedTarget = trimToNull(targetName);
        return normalizedTarget == null || normalizedTarget.equals("경매 #" + referenceSn);
    }

    private void applyAuctionTitle(
            String referenceTypeCode,
            Long referenceSn,
            String currentTargetName,
            java.util.function.Consumer<String> targetNameSetter,
            Map<Long, String> titles) {
        if (!shouldResolveAuctionTarget(referenceTypeCode, referenceSn, currentTargetName)) {
            return;
        }
        String title = titles.get(referenceSn);
        if (title != null && !title.isBlank()) {
            targetNameSetter.accept(title);
        } else if (trimToNull(currentTargetName) == null) {
            targetNameSetter.accept("경매 #" + referenceSn);
        }
    }

    /** 담당자 7 - F-OPS-007: 제재 적용과 신고 확정을 한 트랜잭션에서 처리하도록 신고 행을 잠급니다. */
    @Transactional
    public AbuseReport lockForAdminDecision(Long reportSn) {
        if (reportSn == null || reportSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        AbuseReport report = abuseReportMapper.findReportByIdForUpdate(reportSn);
        if (report == null) {
            throw new CustomException(ErrorCode.ABUSE_REPORT_NOT_FOUND);
        }
        return report;
    }

    /** 담당자 7 · F-OPS-005/007: 관리자 신고 처리에서 거래 판정이 필요한 사건인지 확인합니다. */
    @Transactional(readOnly = true)
    public boolean hasTradeContext(Long reportSn) {
        if (reportSn == null || reportSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return abuseReportMapper.existsTradeContext(reportSn);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOtherActiveReportLinkedToTrade(
            Long tradeSn,
            Long excludedReportSn) {
        if (tradeSn == null || tradeSn <= 0
                || (excludedReportSn != null && excludedReportSn <= 0)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return abuseReportMapper.existsOtherActiveReportLinkedToTrade(
                tradeSn,
                excludedReportSn,
                RECEIVED_STATUS,
                PROCESSING_STATUS);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOtherActiveReportLinkedToAuction(
            Long auctionSn,
            Long excludedReportSn) {
        if (auctionSn == null || auctionSn <= 0
                || (excludedReportSn != null && excludedReportSn <= 0)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return abuseReportMapper.existsOtherActiveReportLinkedToAuction(
                auctionSn,
                excludedReportSn,
                RECEIVED_STATUS,
                PROCESSING_STATUS);
    }

    /**
     * 담당자 7 · F-OPS-007/F-PAY-010/F-PAY-012: 활성 신고의 피신고자만 제한 대상으로 반환합니다.
     * 상위 환전 트랜잭션이 계좌 조회로 이미 스냅샷을 만들었더라도 최신 커밋 상태를 읽도록
     * 별도 READ_COMMITTED 읽기 트랜잭션을 사용합니다.
     */
    @Override
    @Transactional(
            readOnly = true,
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED)
    public boolean hasActiveReportAgainst(Long reportedUserSn) {
        if (reportedUserSn == null || reportedUserSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return abuseReportMapper.existsActiveReportAgainst(
                reportedUserSn,
                RECEIVED_STATUS,
                PROCESSING_STATUS);
    }


    /** 담당자 7 · F-COM-018: 참조 유형·번호·피신고자는 함께 오도록 모양을 먼저 검증합니다. */
    private void validateCustomerReferenceShape(
            String referenceTypeCode,
            Long referenceSn,
            Long reportedUserSn) {
        if ((referenceTypeCode == null) != (referenceSn == null)
                || (referenceSn != null && referenceSn <= 0)
                || (referenceSn != null && reportedUserSn == null)
                || (reportedUserSn != null && referenceSn == null)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /** 담당자 7 · F-COM-018: 중복 파일번호와 타인·타용도 파일 연결을 접수 전에 차단합니다. */
    private List<Long> validateReportFiles(List<Long> requestedFileSns, Long reporterUserSn) {
        if (requestedFileSns == null || requestedFileSns.isEmpty()) {
            return List.of();
        }
        if (requestedFileSns.size() > MAX_REPORT_FILES
                || requestedFileSns.stream().anyMatch(fileSn -> fileSn == null || fileSn <= 0)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        List<Long> normalized = new LinkedHashSet<>(requestedFileSns).stream().toList();
        if (normalized.size() != requestedFileSns.size()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        // 여러 신고가 같은 파일 집합을 동시에 연결해도 잠금 순서가 뒤집히지 않게 번호순으로 검증한다.
        normalized.stream().sorted().forEach(fileSn ->
                fileStorageService.requireOwnedAbuseReportFile(fileSn, reporterUserSn));
        return normalized;
    }

    private void attachReportFiles(MyAbuseReportResponse report) {
        report.setFiles(abuseReportMapper.findReportFiles(report.getReportSn()));
    }

    private LockEntry acquireCustomerReportLock(CustomerReportKey key) {
        LockEntry lockEntry = customerReportLocks.compute(key, (ignored, current) -> {
            LockEntry selected = current == null ? new LockEntry() : current;
            selected.users.incrementAndGet();
            return selected;
        });
        lockEntry.lock.lock();
        return lockEntry;
    }

    private boolean registerCustomerReportLockRelease(CustomerReportKey key, LockEntry lockEntry) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return true;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                releaseCustomerReportLock(key, lockEntry);
            }
        });
        return false;
    }

    private void releaseCustomerReportLock(CustomerReportKey key, LockEntry lockEntry) {
        try {
            lockEntry.lock.unlock();
        } finally {
            customerReportLocks.computeIfPresent(key, (ignored, current) -> {
                if (current != lockEntry) {
                    return current;
                }
                return current.users.decrementAndGet() == 0 ? null : current;
            });
        }
    }

    private void validateAutomaticReport(SensitiveDetectionReportCommand command) {
        if (command == null
                || command.riskEventSn() == null
                || command.riskEventSn() <= 0
                || command.reportedUserSn() == null
                || command.reportedUserSn() <= 0
                || command.detectedTypes() == null
                || command.detectedTypes().isEmpty()
                || command.detectedTypes().stream().anyMatch(type -> type == null)
                || (command.referenceTypeCode() == null) != (command.referenceSn() == null)
                || (command.referenceTypeCode() != null
                        && (command.referenceTypeCode().isBlank()
                                || command.referenceTypeCode().trim().length() > 30))
                || (command.referenceSn() != null && command.referenceSn() <= 0)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (authMemberPort.findById(command.reportedUserSn()).isEmpty()) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private void validateCustomerReportedUser(Long reporterUserSn, Long reportedUserSn) {
        if (reportedUserSn == null) {
            return;
        }
        if (reportedUserSn <= 0 || reporterUserSn.equals(reportedUserSn)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (authMemberPort.findById(reportedUserSn).isEmpty()) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private ManualReportValues validateManualReport(
            Long reporterUserSn,
            ManualAbuseReportRequest request) {
        if (reporterUserSn == null
                || reporterUserSn <= 0
                || request == null
                || request.reportedUserSn() == null
                || request.reportedUserSn() <= 0
                || request.referenceTypeCode() == null
                || request.referenceTypeCode().isBlank()
                || request.referenceTypeCode().trim().length() > 30
                || request.referenceSn() == null
                || request.referenceSn() <= 0
                || (request.content() != null && request.content().trim().length() > 4000)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        String referenceTypeCode = request.referenceTypeCode().trim();
        if (!PRODUCT_COMMENT_REFERENCE_TYPE.equals(referenceTypeCode)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        InquiryReportTarget target = productServiceProvider.getObject()
                .getInquiryReportTarget(request.referenceSn());
        if (target == null
                || !request.referenceSn().equals(target.getPrdCmtSn())
                || !BUYER_INQUIRY_TYPE.equals(target.getPrdCmtTypeCd())
                || target.getWriterUsrSn() == null
                || target.getSellerUsrSn() == null
                || !request.reportedUserSn().equals(target.getWriterUsrSn())
                || reporterUserSn.equals(target.getWriterUsrSn())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!reporterUserSn.equals(target.getSellerUsrSn())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        String content = trimToNull(request.content());
        return new ManualReportValues(
                reporterUserSn,
                request.reportedUserSn(),
                referenceTypeCode,
                request.referenceSn(),
                content == null ? DEFAULT_MANUAL_REPORT_CONTENT : content);
    }

    /** 담당자 7 · F-COM-018: 신규 고객 신고는 확정된 세부 사유 7종만 허용합니다. */
    private void validateCustomerReportType(String reportTypeCode) {
        if (!CUSTOMER_REPORT_TYPES.contains(reportTypeCode)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        referenceDataService.requireActiveCode(REPORT_TYPE_GROUP, reportTypeCode);
    }

    private void validateReferenceCodes(String reportTypeCode, String referenceTypeCode) {
        referenceDataService.requireActiveCode(REPORT_TYPE_GROUP, reportTypeCode);
        referenceDataService.requireActiveCode(REPORT_STATUS_GROUP, RECEIVED_STATUS);
        if (referenceTypeCode != null) {
            referenceDataService.requireActiveCode(
                    REFERENCE_TYPE_GROUP,
                    referenceTypeCode.trim());
        }
    }

    private void rejectDuplicateManualReport(ManualReportValues values) {
        Long existingReportSn = abuseReportMapper.findManualReportId(
                values.reporterUserSn(),
                values.referenceTypeCode(),
                values.referenceSn());
        if (existingReportSn != null) {
            throw new CustomException(ErrorCode.ABUSE_REPORT_ALREADY_EXISTS);
        }
    }

    private String validateManualReferenceType(
            Long reporterUserSn,
            String referenceTypeCode) {
        if (reporterUserSn == null || reporterUserSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return normalizeManualReferenceType(referenceTypeCode);
    }

    private String normalizeManualReferenceType(String referenceTypeCode) {
        if (referenceTypeCode == null
                || referenceTypeCode.isBlank()
                || referenceTypeCode.trim().length() > 30) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String normalized = referenceTypeCode.trim();
        if (!PRODUCT_COMMENT_REFERENCE_TYPE.equals(normalized)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }

    private DecisionValues validateDecision(AdminReportDecisionCommand command) {
        if (command == null
                || command.reportSn() == null
                || command.reportSn() <= 0
                || command.decision() == null
                || command.reason() == null
                || command.reason().isBlank()
                || command.reason().trim().length() > MAX_PROCESS_REASON_LENGTH
                || command.requestId() == null
                || command.requestId().isBlank()
                || command.requestId().trim().length() > MAX_REQUEST_ID_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        String adminId = normalizeAdminId(command.adminId());
        String expectedStatus;
        String newStatus;
        String auditAction;
        boolean finalDecision;
        switch (command.decision()) {
            case PROCESSING -> {
                expectedStatus = RECEIVED_STATUS;
                newStatus = PROCESSING_STATUS;
                auditAction = "STATUS_CHANGE";
                finalDecision = false;
            }
            case PROCESSED -> {
                expectedStatus = PROCESSING_STATUS;
                newStatus = PROCESSED_STATUS;
                auditAction = "ADMIN_APPROVE";
                finalDecision = true;
            }
            case REJECTED -> {
                expectedStatus = PROCESSING_STATUS;
                newStatus = REJECTED_STATUS;
                auditAction = "ADMIN_REJECT";
                finalDecision = true;
            }
            default -> throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return new DecisionValues(
                adminId,
                command.reason().trim(),
                command.requestId().trim(),
                expectedStatus,
                newStatus,
                auditAction,
                finalDecision);
    }

    private String normalizeAdminId(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String normalized = actorId.trim().replaceFirst("(?i)^USR:", "");
        try {
            if (Long.parseLong(normalized) <= 0) {
                throw new NumberFormatException();
            }
            return normalized;
        } catch (NumberFormatException exception) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String automaticReportContent(SensitiveDetectionReportCommand command) {
        String types = command.detectedTypes().stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
        return "민감정보 자동 탐지: " + types;
    }

    private String statusSummary(Long reportSn, String statusCode) {
        return "reportSn=" + reportSn + ",status=" + statusCode;
    }

    private String decisionResult(String statusCode) {
        return PROCESSED_STATUS.equals(statusCode) ? "처리 완료" : "반려";
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record ManualReportValues(
            Long reporterUserSn,
            Long reportedUserSn,
            String referenceTypeCode,
            Long referenceSn,
            String content) {
    }

    private record DecisionValues(
            String adminId,
            String reason,
            String requestId,
            String expectedStatusCode,
            String newStatusCode,
            String auditAction,
            boolean finalDecision) {
    }

    private record CustomerReportKey(
            Long reporterUserSn,
            Long reportedUserSn,
            String reportTypeCode,
            String referenceTypeCode,
            Long referenceSn) {
    }

    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicInteger users = new AtomicInteger();
    }
}
