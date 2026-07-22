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
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.operation.port.SellerCancellationDecision;
import nct.ops.operation.port.SellerCancellationDecisionCommand;
import nct.ops.operation.port.SellerCancellationDecisionPort;

/**
 * 담당자 7 · F-OPS-004/F-OPS-015: 판매자 취소 판정을 거래 상태 변경 계약과 감사 기록 계약에 연결합니다.
 */
@Service
@RequiredArgsConstructor
public class AdminSellerCancellationService {

    private static final String REF_TRADE = "TRADE";
    private static final String ACTION_APPROVE = "ADMIN_APPROVE";
    private static final String ACTION_REJECT = "ADMIN_REJECT";

    private final SellerCancellationDecisionPort sellerCancellationDecisionPort;
    private final AuditLogPort auditLogPort;

    @Transactional
    public void decide(
            Long tradeSn,
            SellerCancellationDecision decision,
            String reason,
            Long adminUserId) {
        validate(tradeSn, decision, reason, adminUserId);

        String normalizedReason = reason.trim();
        String requestId = requestId(adminUserId, tradeSn, decision, normalizedReason);

        sellerCancellationDecisionPort.decide(new SellerCancellationDecisionCommand(
                tradeSn,
                decision,
                normalizedReason,
                String.valueOf(adminUserId),
                requestId));

        auditLogPort.record(new AuditLogCommand(
                auditAction(decision),
                String.valueOf(adminUserId),
                REF_TRADE,
                tradeSn,
                normalizedReason,
                "sellerCancellationDecision=pending",
                "sellerCancellationDecision=" + decision.name(),
                requestId));
    }

    private void validate(
            Long tradeSn,
            SellerCancellationDecision decision,
            String reason,
            Long adminUserId) {
        if (tradeSn == null
                || tradeSn <= 0
                || decision == null
                || reason == null
                || reason.isBlank()
                || reason.trim().length() > 1000
                || adminUserId == null
                || adminUserId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String auditAction(SellerCancellationDecision decision) {
        return decision == SellerCancellationDecision.APPROVED ? ACTION_APPROVE : ACTION_REJECT;
    }

    private String requestId(
            Long adminUserId,
            Long tradeSn,
            SellerCancellationDecision decision,
            String reason) {
        return "seller-cancel:" + sha256(adminUserId + "|" + tradeSn + "|" + decision + "|" + reason);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
