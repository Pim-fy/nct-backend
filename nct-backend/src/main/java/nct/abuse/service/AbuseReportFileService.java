package nct.abuse.service;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.abuse.mapper.AbuseReportMapper;
import nct.audit.domain.AuditLogType;
import nct.audit.service.AuditLogService;
import nct.file.domain.FileMeta;
import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

/** 담당자 7 · F-COM-018/F-OPS-007: 신고 첨부의 연결·권한·실파일을 검증합니다. */
@Service
@RequiredArgsConstructor
public class AbuseReportFileService {

    private final AbuseReportMapper abuseReportMapper;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public ReportFileDownload getForReporter(long reporterUserSn, long reportSn, long fileSn) {
        validateIds(reporterUserSn, reportSn, fileSn);
        if (abuseReportMapper.findMyReportById(reportSn, reporterUserSn) == null) {
            throw new CustomException(ErrorCode.ABUSE_REPORT_NOT_FOUND);
        }
        return requireLinkedFile(reportSn, fileSn);
    }

    @Transactional
    public ReportFileDownload getForAdmin(
            long adminUserSn,
            long reportSn,
            long fileSn,
            String reason,
            String ipAddress) {
        validateIds(adminUserSn, reportSn, fileSn);
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty()) {
            throw new CustomException(
                    ErrorCode.MISSING_REQUIRED_FIELD,
                    "신고 첨부 원문 열람 사유를 입력해야 합니다.");
        }
        if (normalizedReason.length() > 1000) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "신고 첨부 원문 열람 사유는 1,000자 이하여야 합니다.");
        }
        if (abuseReportMapper.findReportDetailById(reportSn) == null) {
            throw new CustomException(ErrorCode.ABUSE_REPORT_NOT_FOUND);
        }

        ReportFileDownload download = requireLinkedFile(reportSn, fileSn);
        auditLogService.record(
                adminUserSn,
                AuditLogType.SENSITIVE_VIEW,
                null,
                null,
                "신고 #" + reportSn + " 첨부 파일 #" + fileSn
                        + " 열람 - 사유: " + normalizedReason,
                ipAddress);
        return download;
    }

    private ReportFileDownload requireLinkedFile(long reportSn, long fileSn) {
        if (abuseReportMapper.countReportFileLink(reportSn, fileSn) == 0) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        }
        FileMeta fileMeta = fileStorageService.requireAbuseReportFile(fileSn);
        Path diskPath = fileStorageService.diskPathOf(fileMeta);
        if (!Files.isRegularFile(diskPath)) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        }
        return new ReportFileDownload(fileMeta, diskPath);
    }

    private void validateIds(long actorUserSn, long reportSn, long fileSn) {
        if (actorUserSn <= 0 || reportSn <= 0 || fileSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    public record ReportFileDownload(FileMeta fileMeta, Path diskPath) {
    }
}
