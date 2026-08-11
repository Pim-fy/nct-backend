package nct.ops.operation.service;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.audit.domain.AuditLogType;
import nct.audit.service.AuditLogService;
import nct.common.domain.RefType;
import nct.file.domain.FileMeta;
import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.trade.port.AdminTradeDisputeReader;

/** 담당자 7 · F-OPS-005: 분쟁 연결·파일 유형·실파일·감사 기록을 검증한 뒤 증빙을 엽니다. */
@Service
@RequiredArgsConstructor
public class AdminDisputeEvidenceFileService {

    private final AdminTradeDisputeReader disputeReader;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;

    @Transactional
    public EvidenceDownload getForAdmin(
            long adminUserSn,
            long disputeSn,
            long fileSn,
            String reason,
            String ipAddress) {
        if (adminUserSn <= 0 || disputeSn <= 0 || fileSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty()) {
            throw new CustomException(
                    ErrorCode.MISSING_REQUIRED_FIELD,
                    "증빙 원문 열람 사유를 입력해야 합니다.");
        }
        if (normalizedReason.length() > 1000) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "증빙 원문 열람 사유는 1,000자 이하여야 합니다.");
        }
        if (!disputeReader.hasEvidenceFile(disputeSn, fileSn)) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        }

        FileMeta fileMeta = fileStorageService.requireTradeDisputeFile(fileSn);
        Path diskPath = fileStorageService.diskPathOf(fileMeta);
        if (!Files.isRegularFile(diskPath)) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        }

        auditLogService.record(
                adminUserSn,
                AuditLogType.SENSITIVE_VIEW,
                RefType.TRADE_DISPUTE,
                disputeSn,
                "거래 분쟁 증빙 열람 - 파일 #" + fileSn + " - 사유: " + normalizedReason,
                ipAddress);
        return new EvidenceDownload(fileMeta, diskPath);
    }

    public record EvidenceDownload(FileMeta fileMeta, Path diskPath) {
    }
}
