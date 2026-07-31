package nct.abuse.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.abuse.domain.AbuseReport;
import nct.abuse.dto.AdminAbuseReportResponse;
import nct.abuse.dto.CustomerAbuseReportRequest;
import nct.abuse.dto.CustomerSupportReportRequest;
import nct.abuse.dto.ManualAbuseReportRequest;
import nct.abuse.dto.ManualAbuseReportResponse;
import nct.abuse.dto.ManualAbuseReportStatusResponse;
import nct.abuse.dto.MyAbuseReportResponse;
import nct.global.response.PageResponse;
import nct.abuse.mapper.AbuseReportMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.operation.port.AdminReportDecision;
import nct.ops.operation.port.AdminReportDecisionCommand;
import nct.ops.operation.port.AdminReportDecisionPort;
import nct.ops.reference.service.ReferenceDataService;
import nct.ops.security.port.SensitiveDetectionReportCommand;
import nct.ops.security.port.SensitiveDetectionReportPort;
import nct.ops.security.port.SensitiveDetectionReportResult;
import nct.product.dto.InquiryReportTarget;
import nct.product.service.ProductService;

@Service
@RequiredArgsConstructor
public class AbuseReportService implements SensitiveDetectionReportPort, AdminReportDecisionPort {

    static final String CONTENT_REPORT_TYPE = "ABRC0001";
    static final String RECEIVED_STATUS = "ABRC0005";
    static final String PROCESSING_STATUS = "ABRC0006";
    static final String PROCESSED_STATUS = "ABRC0007";
    static final String REJECTED_STATUS = "ABRC0008";
    static final String PRODUCT_COMMENT_REFERENCE_TYPE = "REFC0012";

    private static final String REPORT_TYPE_GROUP = "ABRG01";
    private static final String REPORT_STATUS_GROUP = "ABRG02";
    private static final String REFERENCE_TYPE_GROUP = "REFG01";
    private static final String SYSTEM_ACTOR = "SYSTEM";
    private static final String BUYER_INQUIRY_TYPE = "PRDC0006";
    private static final String DEFAULT_MANUAL_REPORT_CONTENT = "상품 댓글·문의 신고";
    private static final int MAX_PROCESS_REASON_LENGTH = 4000;
    private static final int MAX_REQUEST_ID_LENGTH = 200;
    private static final int MAX_PUBLIC_REFERENCE_LOOKUP_SIZE = 100;
    private static final Set<String> DECIDABLE_STATUSES = Set.of(
            RECEIVED_STATUS,
            PROCESSING_STATUS);

    private final AbuseReportMapper abuseReportMapper;
    private final ReferenceDataService referenceDataService;
    private final AuditLogPort auditLogPort;
    private final NotificationService notificationService;
    private final ObjectProvider<ProductService> productServiceProvider;

    /** F-COM-018: 로그인 사용자가 고객센터형 신고를 접수한다. */
    @Transactional
    public ManualAbuseReportResponse submitCustomerReport(
            Long reporterUserSn,
            CustomerAbuseReportRequest request) {
        if (reporterUserSn == null || reporterUserSn <= 0 || request == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        referenceDataService.requireActiveCode(REPORT_TYPE_GROUP, request.reportTypeCode());
        referenceDataService.requireActiveCode(REPORT_STATUS_GROUP, RECEIVED_STATUS);
        if (request.referenceTypeCode() != null && !request.referenceTypeCode().isBlank()) {
            referenceDataService.requireActiveCode(REFERENCE_TYPE_GROUP, request.referenceTypeCode().trim());
        }

        String actorId = String.valueOf(reporterUserSn);
        String refTypeCode = (request.referenceTypeCode() == null || request.referenceTypeCode().isBlank())
                ? null : request.referenceTypeCode().trim();

        AbuseReport report = AbuseReport.builder()
                .riskEventSn(null)
                .reporterUserSn(reporterUserSn)
                .reportedUserSn(request.reportedUserSn())
                .reportTypeCode(request.reportTypeCode())
                .statusCode(RECEIVED_STATUS)
                .referenceTypeCode(refTypeCode)
                .referenceSn(request.referenceSn())
                .title(request.title().trim())
                .targetName(request.targetName() == null ? null : request.targetName().trim())
                .content(request.content().trim())
                .registeredBy(actorId)
                .updatedBy(actorId)
                .build();

        int inserted = abuseReportMapper.insertCustomerReport(report);
        if (inserted != 1 || report.getReportSn() == null) {
            throw new CustomException(ErrorCode.DATABASE_ERROR);
        }
        return new ManualAbuseReportResponse(report.getReportSn());
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

        Long existingReport = abuseReportMapper.findManualReportId(
                reporterUserSn, PRODUCT_COMMENT_REFERENCE_TYPE, request.targetSn());
        if (existingReport != null) {
            throw new CustomException(ErrorCode.ABUSE_REPORT_ALREADY_EXISTS);
        }

        validateReferenceCodes(PRODUCT_COMMENT_REFERENCE_TYPE);
        String actorId = String.valueOf(reporterUserSn);
        AbuseReport report = AbuseReport.builder()
                .riskEventSn(null)
                .reporterUserSn(reporterUserSn)
                .reportedUserSn(target.getWriterUsrSn())
                .reportTypeCode(CONTENT_REPORT_TYPE)
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
        validateReferenceCodes(values.referenceTypeCode());
        rejectDuplicateManualReport(values);

        String actorId = String.valueOf(values.reporterUserSn());
        AbuseReport report = AbuseReport.builder()
                .riskEventSn(null)
                .reporterUserSn(values.reporterUserSn())
                .reportedUserSn(values.reportedUserSn())
                .reportTypeCode(CONTENT_REPORT_TYPE)
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
        validateReferenceCodes(command.referenceTypeCode());

        AbuseReport report = AbuseReport.builder()
                .riskEventSn(command.riskEventSn())
                .reporterUserSn(null)
                .reportedUserSn(null)
                .reportTypeCode(CONTENT_REPORT_TYPE)
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
        if (!DECIDABLE_STATUSES.contains(report.getStatusCode())) {
            throw new CustomException(ErrorCode.ABUSE_REPORT_ALREADY_PROCESSED);
        }

        referenceDataService.requireActiveCode(REPORT_STATUS_GROUP, values.newStatusCode());
        int updated = abuseReportMapper.updateDecision(
                report.getReportSn(),
                report.getStatusCode(),
                values.newStatusCode(),
                values.reason(),
                values.adminId());
        if (updated != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "신고 상태가 이미 변경되었습니다.");
        }

        auditLogPort.record(new AuditLogCommand(
                values.auditAction(),
                values.adminId(),
                report.getReferenceTypeCode(),
                report.getReferenceSn(),
                values.reason(),
                statusSummary(report.getReportSn(), report.getStatusCode()),
                statusSummary(report.getReportSn(), values.newStatusCode()),
                values.requestId()));

        // 담당자 7 · F-OPS-007: 일반 신고에만 처리 결과를 알리고,
        // 신고자가 없는 SYSTEM 자동 탐지 신고에는 사용자 알림을 만들지 않는다.
        if (report.getReporterUserSn() != null && report.getReporterUserSn() > 0) {
            notificationService.notifyReportResult(
                    report.getReporterUserSn(),
                    report.getReportSn(),
                    decisionResult(values.newStatusCode()));
        }
    }

    /** 접수·처리중 상태의 신고를 자동·일반 신고 구분 없이 오래된 순서로 조회한다. */
    @Transactional(readOnly = true)
    public List<AdminAbuseReportResponse> getPendingReports() {
        return abuseReportMapper.findPendingReports(RECEIVED_STATUS, PROCESSING_STATUS);
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
        return report;
    }

    private void validateAutomaticReport(SensitiveDetectionReportCommand command) {
        if (command == null
                || command.riskEventSn() == null
                || command.riskEventSn() <= 0
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

    private void validateReferenceCodes(String referenceTypeCode) {
        referenceDataService.requireActiveCode(REPORT_TYPE_GROUP, CONTENT_REPORT_TYPE);
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
        String newStatus = command.decision() == AdminReportDecision.PROCESSED
                ? PROCESSED_STATUS : REJECTED_STATUS;
        String auditAction = command.decision() == AdminReportDecision.PROCESSED
                ? "ADMIN_APPROVE" : "ADMIN_REJECT";
        return new DecisionValues(
                adminId,
                command.reason().trim(),
                command.requestId().trim(),
                newStatus,
                auditAction);
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
        return value == null ? null : value.trim();
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
            String newStatusCode,
            String auditAction) {
    }
}
