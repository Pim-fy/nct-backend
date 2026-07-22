package nct.ops.operation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.operation.port.AdminReportDecision;
import nct.ops.operation.port.AdminReportDecisionCommand;
import nct.ops.operation.port.AdminReportDecisionPort;

/** 담당자 7 · F-OPS-007: 관리자 신고 처리 API와 담당자5 신고 서비스 계약을 연결합니다. */
@Service
@RequiredArgsConstructor
public class AdminReportOperationService {

    private final AdminReportDecisionPort adminReportDecisionPort;

    @Transactional
    public void decide(
            Long reportSn,
            AdminReportDecision decision,
            String reason,
            Long adminUserId) {
        validate(reportSn, decision, reason, adminUserId);

        String normalizedReason = reason.trim();
        adminReportDecisionPort.decide(new AdminReportDecisionCommand(
                reportSn,
                decision,
                normalizedReason,
                String.valueOf(adminUserId),
                requestId(adminUserId, reportSn, decision, normalizedReason)));
    }

    private void validate(
            Long reportSn,
            AdminReportDecision decision,
            String reason,
            Long adminUserId) {
        if (reportSn == null
                || reportSn <= 0
                || decision == null
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
            String reason) {
        return "admin-report:" + sha256(adminUserId + "|" + reportSn + "|" + decision + "|" + reason);
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
