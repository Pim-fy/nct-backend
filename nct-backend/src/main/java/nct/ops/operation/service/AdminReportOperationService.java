package nct.ops.operation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.abuse.dto.AdminAbuseReportResponse;
import nct.abuse.service.AbuseReportService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.PageResponse;
import nct.member.dto.AdminMemberIdentityResponse;
import nct.member.port.AdminMemberIdentityReader;
import nct.ops.operation.dto.AdminReportPageResponse;
import nct.ops.operation.domain.ReportEnforcementAction;
import nct.ops.operation.port.AdminReportDecision;
import nct.ops.sanction.domain.SanctionRecord;
import nct.ops.sanction.dto.SanctionImpactResponse;
import nct.ops.sanction.mapper.SanctionImpactMapper;
import nct.ops.sanction.service.ReportEnforcementService;
import nct.ops.sanction.service.ReportSanctionService;

/** 담당자 7 · F-OPS-007: 관리자 신고 관리 API와 담당자 3 신고 서비스 계약을 연결합니다. */
@Service
@RequiredArgsConstructor
public class AdminReportOperationService {

    private final AbuseReportService abuseReportService;
    private final AdminMemberIdentityReader memberIdentityReader;
    private final ReportEnforcementService reportEnforcementService;
    private final ReportSanctionService reportSanctionService;
    private final SanctionImpactMapper sanctionImpactMapper;

    @Transactional(readOnly = true)
    public List<AdminAbuseReportResponse> getPendingReports() {
        List<AdminAbuseReportResponse> reports = abuseReportService.getPendingReports();
        enrichSanctions(reports);
        return reports;
    }

    @Transactional(readOnly = true)
    public AdminReportPageResponse getReports(
            String statusCode,
            String keyword,
            String caseType,
            int page,
            int size) {
        PageResponse<AdminAbuseReportResponse> result = abuseReportService.getAdminReports(
                statusCode,
                keyword,
                caseType,
                page,
                size);
        List<AdminAbuseReportResponse> reports = enrichMembers(result.getContent());
        enrichSanctions(reports);
        return AdminReportPageResponse.builder()
                .items(reports)
                .page(result.getPage())
                .size(result.getSize())
                .totalItems(result.getTotalCount())
                .totalPages(result.getTotalCount() == 0
                        ? 0
                        : (int) ((result.getTotalCount() + result.getSize() - 1) / result.getSize()))
                .build();
    }

    @Transactional(readOnly = true)
    public AdminAbuseReportResponse getReportDetail(Long reportSn) {
        AdminAbuseReportResponse report = enrichMember(abuseReportService.getReportDetail(reportSn));
        enrichSanction(report);
        return report;
    }

    private List<AdminAbuseReportResponse> enrichMembers(List<AdminAbuseReportResponse> reports) {
        if (reports == null || reports.isEmpty()) {
            return reports == null ? List.of() : reports;
        }

        Set<Long> userSns = new LinkedHashSet<>();
        for (AdminAbuseReportResponse report : reports) {
            addUserSn(userSns, report.getReporterUserSn());
            addUserSn(userSns, report.getReportedUserSn());
            addUserSn(userSns, numericUserSn(report.getProcessedBy()));
        }
        Map<Long, AdminMemberIdentityResponse> identities = memberIdentityReader.findByUserSns(userSns);
        reports.forEach(report -> applyMembers(report, identities));
        return reports;
    }

    private AdminAbuseReportResponse enrichMember(AdminAbuseReportResponse report) {
        if (report == null) {
            return null;
        }
        enrichMembers(List.of(report));
        return report;
    }

    private void applyMembers(
            AdminAbuseReportResponse report,
            Map<Long, AdminMemberIdentityResponse> identities) {
        report.setReporterMember(identityOf(identities, report.getReporterUserSn()));
        report.setReportedMember(identityOf(identities, report.getReportedUserSn()));
        report.setProcessorMember(identityOf(identities, numericUserSn(report.getProcessedBy())));
    }

    private AdminMemberIdentityResponse identityOf(
            Map<Long, AdminMemberIdentityResponse> identities,
            Long userSn) {
        return userSn == null ? null : identities.get(userSn);
    }

    private void addUserSn(Set<Long> userSns, Long userSn) {
        if (userSn != null && userSn > 0) {
            userSns.add(userSn);
        }
    }

    private Long numericUserSn(String value) {
        if (value == null || !value.matches("\\d+")) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Transactional
    public void decide(
            Long reportSn,
            AdminReportDecision decision,
            ReportEnforcementAction enforcementAction,
            String reason,
            Long adminUserId) {
        validate(reportSn, decision, enforcementAction, reason, adminUserId);

        String normalizedReason = reason.trim();
        String requestId = requestId(
                adminUserId, reportSn, decision, enforcementAction, normalizedReason);
        reportEnforcementService.decide(
                reportSn,
                decision,
                enforcementAction,
                normalizedReason,
                adminUserId,
                requestId);
    }

    @Transactional
    public void releaseSanction(Long reportSn, String reason, Long adminUserId) {
        if (reportSn == null || reportSn <= 0
                || reason == null || reason.isBlank() || reason.trim().length() > 1000
                || adminUserId == null || adminUserId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String normalizedReason = reason.trim();
        String requestId = "admin-report-release:" + sha256(
                adminUserId + "|" + reportSn + "|" + normalizedReason);
        reportEnforcementService.releaseByReport(
                reportSn, adminUserId, normalizedReason, requestId);
    }

    private void validate(
            Long reportSn,
            AdminReportDecision decision,
            ReportEnforcementAction enforcementAction,
            String reason,
            Long adminUserId) {
        if (reportSn == null
                || reportSn <= 0
                || decision == null
                || enforcementAction == null
                || reason == null
                || reason.isBlank()
                || reason.trim().length() > 4000
                || adminUserId == null
                || adminUserId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String requestId(
            Long adminUserId,
            Long reportSn,
            AdminReportDecision decision,
            ReportEnforcementAction enforcementAction,
            String reason) {
        return "admin-report:" + sha256(
                adminUserId + "|" + reportSn + "|" + decision + "|" + enforcementAction + "|" + reason);
    }

    private void enrichSanctions(List<AdminAbuseReportResponse> reports) {
        if (reports == null) {
            return;
        }
        reports.forEach(this::enrichSanction);
    }

    private void enrichSanction(AdminAbuseReportResponse report) {
        if (report == null) {
            return;
        }
        SanctionRecord sanction = reportSanctionService.findByReport(report.getReportSn());
        if (sanction == null) {
            report.setEnforcementAction(ReportEnforcementAction.NONE.name());
            report.setSanctionImpacts(List.of());
            return;
        }
        report.setEnforcementAction(sanction.getEndedAt() == null
                ? ReportEnforcementAction.PERMANENT_SUSPENSION.name()
                : ReportEnforcementAction.TEMPORARY_SUSPENSION_7_DAYS.name());
        report.setSanctionSn(sanction.getSanctionSn());
        report.setSanctionStartedAt(sanction.getStartedAt());
        report.setSanctionEndedAt(sanction.getEndedAt());
        report.setSanctionReleased(sanction.getReleaseRequestId() != null);
        report.setSanctionImpacts(sanctionImpactMapper.findByReport(report.getReportSn()).stream()
                .map(impact -> new SanctionImpactResponse(
                        impact.getReferenceTypeCode(),
                        impact.getReferenceSn(),
                        impact.getRoleCode(),
                        impact.getActionCode(),
                        impact.getStatusCode(),
                        impact.getResult()))
                .toList());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
