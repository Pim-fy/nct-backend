package nct.abuse.service;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.abuse.domain.AbuseReport;
import nct.abuse.mapper.AbuseReportMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.operation.port.AdminReportDecision;
import nct.ops.operation.port.AdminReportDecisionCommand;
import nct.ops.operation.port.AdminReportDecisionPort;
import nct.ops.reference.service.ReferenceDataService;
import nct.ops.security.port.SensitiveDetectionReportCommand;
import nct.ops.security.port.SensitiveDetectionReportPort;
import nct.ops.security.port.SensitiveDetectionReportResult;

@Service
@RequiredArgsConstructor
public class AbuseReportService implements SensitiveDetectionReportPort, AdminReportDecisionPort {

    static final String CONTENT_REPORT_TYPE = "ABRC0001";
    static final String RECEIVED_STATUS = "ABRC0005";
    static final String PROCESSING_STATUS = "ABRC0006";
    static final String PROCESSED_STATUS = "ABRC0007";
    static final String REJECTED_STATUS = "ABRC0008";

    private static final String REPORT_TYPE_GROUP = "ABRG01";
    private static final String REPORT_STATUS_GROUP = "ABRG02";
    private static final String REFERENCE_TYPE_GROUP = "REFG01";
    private static final String SYSTEM_ACTOR = "SYSTEM";
    private static final int MAX_PROCESS_REASON_LENGTH = 4000;
    private static final int MAX_REQUEST_ID_LENGTH = 200;
    private static final Set<String> DECIDABLE_STATUSES = Set.of(
            RECEIVED_STATUS,
            PROCESSING_STATUS);

    private final AbuseReportMapper abuseReportMapper;
    private final ReferenceDataService referenceDataService;
    private final AuditLogPort auditLogPort;

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

    private void validateReferenceCodes(String referenceTypeCode) {
        referenceDataService.requireActiveCode(REPORT_TYPE_GROUP, CONTENT_REPORT_TYPE);
        referenceDataService.requireActiveCode(REPORT_STATUS_GROUP, RECEIVED_STATUS);
        if (referenceTypeCode != null) {
            referenceDataService.requireActiveCode(
                    REFERENCE_TYPE_GROUP,
                    referenceTypeCode.trim());
        }
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

    private String trimToNull(String value) {
        return value == null ? null : value.trim();
    }

    private record DecisionValues(
            String adminId,
            String reason,
            String requestId,
            String newStatusCode,
            String auditAction) {
    }
}
