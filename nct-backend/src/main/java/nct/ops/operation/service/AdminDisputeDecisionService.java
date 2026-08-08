package nct.ops.operation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.operation.domain.AdminDisputeDecision;
import nct.ops.operation.domain.AdminDisputeDecisionCommittedEvent;
import nct.ops.operation.dto.AdminDisputeDecisionResponse;
import nct.point.service.PointService;
import nct.settlement.service.SettlementService;
import nct.trade.dto.AdminTradeDisputeDecisionTarget;
import nct.trade.port.AdminTradeDisputeCommandPort;

/**
 * 담당자 7 · F-OPS-006/REQ-PAY-015: 관리자 판정과 거래·정산·전액 환불·감사를
 * 하나의 트랜잭션으로 조립합니다. 알림은 판정 커밋 후 발행하며 부분 금액 입력은 허용하지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class AdminDisputeDecisionService {

    private static final String MATERIAL_TRADE = "TRDC0001";
    private static final String SERVICE_TRADE = "TRDC0002";
    private static final String RECEIVED = "TRDC0016";
    private static final String PROCESSING = "TRDC0017";
    private static final String COMPLETED = "TRDC0018";
    private static final String REJECTED = "TRDC0019";

    private final AdminTradeDisputeCommandPort disputeCommandPort;
    private final SettlementService settlementService;
    private final PointService pointService;
    private final AuditLogPort auditLogPort;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AdminDisputeDecisionResponse decide(
            Long disputeSn,
            AdminDisputeDecision decision,
            String reason,
            Long adminUserSn) {
        validate(disputeSn, decision, reason, adminUserSn);
        String normalizedReason = reason.trim();
        AdminTradeDisputeDecisionTarget target = disputeCommandPort.lockByDisputeSn(disputeSn);

        if (isSameDecision(target, decision)) {
            return response(target, decision, false, 0L);
        }
        requireOpen(target);

        long refundedAmount = switch (decision) {
            case HOLD -> {
                settlementService.holdUpByTradeIfPending(target.getTradeSn(), normalizedReason);
                disputeCommandPort.keepOnHold(
                        target, decision.getResultCode(), normalizedReason, adminUserSn);
                yield 0L;
            }
            case COMPLETE -> {
                disputeCommandPort.restoreAndClose(
                        target, decision.getResultCode(), decision.getStatusCode(),
                        normalizedReason, adminUserSn);
                settlementService.resumeByTradeIfOnHold(target.getTradeSn(), adminUserSn);
                yield 0L;
            }
            case REJECT -> {
                disputeCommandPort.restoreAndClose(
                        target, null, decision.getStatusCode(), normalizedReason, adminUserSn);
                settlementService.resumeByTradeIfOnHold(target.getTradeSn(), adminUserSn);
                yield 0L;
            }
            case REFUND -> refund(target, decision, normalizedReason, adminUserSn);
        };

        recordAudit(target, decision, normalizedReason, adminUserSn);
        publishFinalDecisionNotification(target, decision);
        return AdminDisputeDecisionResponse.builder()
                .disputeSn(target.getDisputeSn())
                .tradeSn(target.getTradeSn())
                .decision(decision.name())
                .disputeStatusCode(decision.getStatusCode())
                .disputeResultCode(decision.getResultCode())
                .changed(true)
                .refundedAmount(refundedAmount)
                .build();
    }

    private long refund(
            AdminTradeDisputeDecisionTarget target,
            AdminDisputeDecision decision,
            String reason,
            Long adminUserSn) {
        EscrowOwner escrowOwner = escrowOwner(target);
        disputeCommandPort.cancelAndClose(
                target, decision.getResultCode(), reason, adminUserSn);
        settlementService.closeRefundedByTradeIfOpen(target.getTradeSn(), adminUserSn);
        return pointService.refundEscrow(
                escrowOwner.userSn(),
                target.getTradeSn(),
                escrowOwner.refType(),
                escrowOwner.refSn(),
                "관리자 거래 분쟁 전액 환불: " + reason);
    }

    private EscrowOwner escrowOwner(AdminTradeDisputeDecisionTarget target) {
        if (MATERIAL_TRADE.equals(target.getTradeTypeCode())) {
            if (target.getBuyerUserSn() == null
                    || target.getBuyerUserSn() <= 0
                    || target.getBidSn() == null
                    || target.getBidSn() <= 0) {
                throw new CustomException(ErrorCode.CONFLICT, "물건 거래의 구매자 또는 보관금 입찰 참조가 없습니다.");
            }
            return new EscrowOwner(target.getBuyerUserSn(), RefType.BID, target.getBidSn());
        }
        if (SERVICE_TRADE.equals(target.getTradeTypeCode())) {
            if (target.getRequesterUserSn() == null || target.getRequesterUserSn() <= 0) {
                throw new CustomException(ErrorCode.CONFLICT, "서비스 거래의 의뢰자 정보가 없습니다.");
            }
            return new EscrowOwner(
                    target.getRequesterUserSn(), RefType.TRADE, target.getTradeSn());
        }
        throw new CustomException(ErrorCode.CONFLICT, "지원하지 않는 거래 유형입니다.");
    }

    private boolean isSameDecision(
            AdminTradeDisputeDecisionTarget target,
            AdminDisputeDecision decision) {
        return switch (decision) {
            case REJECT -> REJECTED.equals(target.getDisputeStatusCode());
            case HOLD -> PROCESSING.equals(target.getDisputeStatusCode())
                    && decision.getResultCode().equals(target.getDisputeResultCode());
            case COMPLETE, REFUND -> COMPLETED.equals(target.getDisputeStatusCode())
                    && decision.getResultCode().equals(target.getDisputeResultCode());
        };
    }

    private void requireOpen(AdminTradeDisputeDecisionTarget target) {
        if (!RECEIVED.equals(target.getDisputeStatusCode())
                && !PROCESSING.equals(target.getDisputeStatusCode())) {
            throw new CustomException(ErrorCode.CONFLICT, "이미 다른 판정으로 종료된 거래 분쟁입니다.");
        }
    }

    private void recordAudit(
            AdminTradeDisputeDecisionTarget target,
            AdminDisputeDecision decision,
            String reason,
            Long adminUserSn) {
        auditLogPort.record(new AuditLogCommand(
                "STATUS_CHANGE",
                String.valueOf(adminUserSn),
                "TRADE_DISPUTE",
                target.getDisputeSn(),
                reason,
                "disputeStatus=" + target.getDisputeStatusCode()
                        + ",tradeStatus=" + target.getTradeStatusCode(),
                "decision=" + decision.name()
                        + ",disputeStatus=" + decision.getStatusCode(),
                requestId(adminUserSn, target.getDisputeSn(), decision, reason)));
    }

    private void publishFinalDecisionNotification(
            AdminTradeDisputeDecisionTarget target,
            AdminDisputeDecision decision) {
        if (decision == AdminDisputeDecision.HOLD) {
            return;
        }
        Set<Long> recipients = new LinkedHashSet<>();
        addRecipient(recipients, target.getSellerUserSn());
        addRecipient(recipients, target.getBuyerUserSn());
        addRecipient(recipients, target.getRequesterUserSn());
        addRecipient(recipients, target.getProviderUserSn());
        eventPublisher.publishEvent(new AdminDisputeDecisionCommittedEvent(
                recipients, target.getDisputeSn(), decision.getLabel()));
    }

    private void addRecipient(Set<Long> recipients, Long userSn) {
        if (userSn != null && userSn > 0) {
            recipients.add(userSn);
        }
    }

    private AdminDisputeDecisionResponse response(
            AdminTradeDisputeDecisionTarget target,
            AdminDisputeDecision decision,
            boolean changed,
            long refundedAmount) {
        return AdminDisputeDecisionResponse.builder()
                .disputeSn(target.getDisputeSn())
                .tradeSn(target.getTradeSn())
                .decision(decision.name())
                .disputeStatusCode(target.getDisputeStatusCode())
                .disputeResultCode(target.getDisputeResultCode())
                .changed(changed)
                .refundedAmount(refundedAmount)
                .build();
    }

    private void validate(
            Long disputeSn,
            AdminDisputeDecision decision,
            String reason,
            Long adminUserSn) {
        if (disputeSn == null
                || disputeSn <= 0
                || decision == null
                || reason == null
                || reason.isBlank()
                || reason.trim().length() > 1000
                || adminUserSn == null
                || adminUserSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String requestId(
            Long adminUserSn,
            Long disputeSn,
            AdminDisputeDecision decision,
            String reason) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String source = adminUserSn + "|" + disputeSn + "|" + decision + "|" + reason;
            return "dispute-decision:"
                    + HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record EscrowOwner(long userSn, RefType refType, long refSn) {
    }
}
