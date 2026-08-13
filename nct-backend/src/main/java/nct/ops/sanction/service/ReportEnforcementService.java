package nct.ops.sanction.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.abuse.domain.AbuseReport;
import nct.abuse.service.AbuseReportService;
import nct.auction.constant.AuctionStatusCode;
import nct.auction.port.AuctionEnforcementImpact;
import nct.auction.port.AuctionEnforcementRestoreCommand;
import nct.auction.port.MemberAuctionEnforcementCommand;
import nct.auction.port.MemberAuctionEnforcementPort;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.member.port.MemberStatusChangeCommand;
import nct.member.port.MemberStatusCommandPort;
import nct.notification.domain.NotificationDomain;
import nct.notification.domain.NotificationType;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.member.port.AccountRestrictionRecoveryPort;
import nct.ops.operation.domain.ReportEnforcementAction;
import nct.ops.operation.port.AdminReportDecision;
import nct.ops.operation.port.AdminReportDecisionCommand;
import nct.ops.sanction.domain.ReportSanctionCreateCommand;
import nct.ops.sanction.domain.ReportSanctionReleaseCommand;
import nct.ops.sanction.domain.SanctionImpactRecord;
import nct.ops.sanction.domain.SanctionRecord;
import nct.ops.sanction.mapper.SanctionImpactMapper;
import nct.quote.port.MemberQuoteEnforcementPort;
import nct.quote.port.QuoteEnforcementImpact;
import nct.servicerequest.port.MemberServiceRequestEnforcementCommand;
import nct.servicerequest.port.MemberServiceRequestEnforcementPort;
import nct.servicerequest.port.ServiceRequestEnforcementImpact;
import nct.servicerequest.port.ServiceRequestEnforcementRestoreCommand;
import nct.trade.port.MemberTradeRestrictionCommand;
import nct.trade.port.MemberTradeRestrictionPort;
import nct.trade.port.MemberTradeRestoreCommand;
import nct.trade.port.RestrictedTrade;
import nct.trade.port.TradeEnforcementImpact;

/**
 * 담당자 7 - F-OPS-007/019/020: 신고 확정, 계정 제재, 관련 업무 조치와 복구를 한 트랜잭션으로 묶습니다.
 */
@Service
@RequiredArgsConstructor
public class ReportEnforcementService implements AccountRestrictionRecoveryPort {

    private static final String ACTIVE_MEMBER = "USRC0001";
    private static final String SUSPENDED_MEMBER = "USRC0002";
    private static final String PROCESSING_REPORT = "ABSC0002";
    private static final String PROCESSED_REPORT = "ABSC0003";
    private static final String ACTIVE_IMPACT = "ACTIVE";
    private static final String RELEASE_PENDING_IMPACT = "RELEASE_PENDING";
    private static final String RESOLVED_IMPACT = "RESOLVED";
    private static final String RESTORED_IMPACT = "RESTORED";
    private static final String SKIPPED_IMPACT = "SKIPPED";

    private final AbuseReportService abuseReportService;
    private final ReportSanctionService reportSanctionService;
    private final SanctionImpactMapper sanctionImpactMapper;
    private final MemberStatusCommandPort memberStatusCommandPort;
    private final MemberAuctionEnforcementPort auctionEnforcementPort;
    private final MemberServiceRequestEnforcementPort serviceRequestEnforcementPort;
    private final MemberQuoteEnforcementPort quoteEnforcementPort;
    private final MemberTradeRestrictionPort tradeRestrictionPort;
    private final AuditLogPort auditLogPort;
    private final NotificationService notificationService;

    @Transactional
    public void decide(
            Long reportSn,
            AdminReportDecision decision,
            ReportEnforcementAction action,
            String reason,
            Long adminUserSn,
            String requestId) {
        validateDecision(reportSn, decision, action, reason, adminUserSn, requestId);

        if (decision != AdminReportDecision.PROCESSED) {
            abuseReportService.decide(decisionCommand(
                    reportSn, decision, reason, adminUserSn, requestId));
            return;
        }

        AbuseReport report = abuseReportService.lockForAdminDecision(reportSn);
        if (PROCESSED_REPORT.equals(report.getStatusCode())
                && requestId.equals(report.getProcessRequestId())) {
            return;
        }
        if (!PROCESSING_REPORT.equals(report.getStatusCode())) {
            throw new CustomException(ErrorCode.ABUSE_REPORT_ALREADY_PROCESSED);
        }

        if (action == ReportEnforcementAction.NONE) {
            abuseReportService.decide(decisionCommand(
                    reportSn, decision, reason, adminUserSn, requestId));
            return;
        }
        if (report.getReportedUserSn() == null || report.getReportedUserSn() <= 0) {
            throw new CustomException(
                    ErrorCode.CONFLICT,
                    "신고 대상 회원이 없는 신고에는 계정 제재를 적용할 수 없습니다.");
        }

        LocalDateTime releaseAt = action == ReportEnforcementAction.TEMPORARY_SUSPENSION_7_DAYS
                ? LocalDateTime.now().plusDays(7)
                : null;
        SanctionRecord sanction = reportSanctionService.create(new ReportSanctionCreateCommand(
                reportSn,
                report.getReportedUserSn(),
                adminUserSn,
                reason,
                releaseAt,
                childRequestId(requestId, "sanction")));

        memberStatusCommandPort.changeStatus(new MemberStatusChangeCommand(
                report.getReportedUserSn(), SUSPENDED_MEMBER, adminUserSn));

        if (action == ReportEnforcementAction.TEMPORARY_SUSPENSION_7_DAYS) {
            applyTemporarySuspension(sanction, reason, adminUserSn, requestId, releaseAt);
        } else {
            applyPermanentSuspension(sanction, reason, adminUserSn, requestId);
        }

        abuseReportService.decide(decisionCommand(
                reportSn, decision, reason, adminUserSn, requestId));
        recordAudit(
                "STATUS_CHANGE",
                report.getReportedUserSn(),
                adminUserSn,
                reason,
                requestId,
                "report=" + reportSn + ",action=NONE",
                "report=" + reportSn + ",action=" + action + ",sanction=" + sanction.getSanctionSn(),
                reportSn);
        notifyRestrictedMember(report.getReportedUserSn(), action, releaseAt);
    }

    @Transactional
    public void releaseByReport(
            Long reportSn,
            Long adminUserSn,
            String reason,
            String requestId) {
        if (reportSn == null || reportSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        SanctionRecord sanction = reportSanctionService.findByReport(reportSn);
        if (sanction == null) {
            throw new CustomException(ErrorCode.NOT_FOUND, "이 신고에 연결된 계정 제재가 없습니다.");
        }
        releaseSanction(sanction, adminUserSn, reason, requestId, false);
    }

    @Transactional
    public void releaseExpired(Long sanctionSn) {
        SanctionRecord sanction = reportSanctionService.findByIdForUpdate(sanctionSn);
        Long actorUserSn = sanction.getProcessorUserSn();
        releaseSanction(
                sanction,
                actorUserSn,
                "7일 이용정지 기간 만료에 따른 자동 해제",
                childRequestId("report-sanction-expire", String.valueOf(sanctionSn)),
                true);
    }

    @Override
    @Transactional
    public void restorePending(Long userSn, Long adminUserSn, String reason) {
        validateRecovery(userSn, adminUserSn, reason);
        if (reportSanctionService.hasActiveSuspension(userSn)) {
            return;
        }

        List<SanctionImpactRecord> impacts =
                sanctionImpactMapper.findUnresolvedTemporaryByUserForUpdate(userSn);
        Map<ImpactKey, List<SanctionImpactRecord>> grouped = new LinkedHashMap<>();
        for (SanctionImpactRecord impact : impacts) {
            grouped.computeIfAbsent(
                    new ImpactKey(impact.getReferenceTypeCode(), impact.getReferenceSn()),
                    ignored -> new ArrayList<>()).add(impact);
        }

        String actorId = String.valueOf(adminUserSn);
        for (Map.Entry<ImpactKey, List<SanctionImpactRecord>> entry : grouped.entrySet()) {
            SanctionImpactRecord baseline = restorableBaseline(entry.getValue());
            if (baseline == null) {
                resolveImpacts(
                        entry.getValue(),
                        SKIPPED_IMPACT,
                        "복구할 수 있는 원래 상태 기록이 없어 자동 복구하지 않았습니다.",
                        actorId);
                continue;
            }
            if (sanctionImpactMapper.countOtherActiveBlockingImpacts(
                    baseline.getSanctionSn(),
                    baseline.getReferenceTypeCode(),
                    baseline.getReferenceSn()) > 0) {
                markReleasePending(entry.getValue(), actorId);
                continue;
            }

            boolean restored = restoreImpact(baseline, adminUserSn, reason);
            resolveImpacts(
                    entry.getValue(),
                    restored ? RESTORED_IMPACT : SKIPPED_IMPACT,
                    restored
                            ? "남아 있던 기간을 기준으로 제재 전 상태를 복구했습니다."
                            : "현재 상태가 달라 자동 복구하지 않았습니다.",
                    actorId);
        }
    }

    private void resolveImpacts(
            List<SanctionImpactRecord> impacts,
            String statusCode,
            String result,
            String actorId) {
        for (SanctionImpactRecord impact : impacts) {
            if (!Set.of(ACTIVE_IMPACT, RELEASE_PENDING_IMPACT)
                    .contains(impact.getStatusCode())) {
                continue;
            }
            if (sanctionImpactMapper.updateStatus(
                    impact.getImpactSn(),
                    impact.getStatusCode(),
                    statusCode,
                    result,
                    actorId) != 1) {
                throw new CustomException(
                        ErrorCode.CONFLICT,
                        "제재 영향 상태가 변경되어 복구 결과를 저장할 수 없습니다.");
            }
        }
    }

    private void applyTemporarySuspension(
            SanctionRecord sanction,
            String reason,
            Long adminUserSn,
            String requestId,
            LocalDateTime releaseAt) {
        MemberAuctionEnforcementCommand auctionCommand = new MemberAuctionEnforcementCommand(
                sanction.getUserSn(),
                adminUserSn,
                reason,
                childRequestId(requestId, "auction"),
                releaseAt,
                sanction.getSourceReportSn());
        for (AuctionEnforcementImpact impact : auctionEnforcementPort.pause(auctionCommand)) {
            insertAuctionImpact(sanction.getSanctionSn(), impact, adminUserSn);
        }

        MemberServiceRequestEnforcementCommand serviceCommand =
                new MemberServiceRequestEnforcementCommand(
                        sanction.getUserSn(), adminUserSn, reason, releaseAt);
        for (ServiceRequestEnforcementImpact impact :
                serviceRequestEnforcementPort.pauseOwned(serviceCommand)) {
            insertServiceRequestImpact(sanction.getSanctionSn(), impact, adminUserSn);
        }

        for (RestrictedTrade trade : tradeRestrictionPort.restrictActiveTrades(
                new MemberTradeRestrictionCommand(sanction.getUserSn(), adminUserSn, reason))
                .restrictedTrades()) {
            insertTradeImpact(sanction.getSanctionSn(), trade, adminUserSn);
        }
    }

    private void applyPermanentSuspension(
            SanctionRecord sanction,
            String reason,
            Long adminUserSn,
            String requestId) {
        MemberAuctionEnforcementCommand auctionCommand = new MemberAuctionEnforcementCommand(
                sanction.getUserSn(),
                adminUserSn,
                reason,
                childRequestId(requestId, "auction"),
                null,
                sanction.getSourceReportSn());
        for (AuctionEnforcementImpact impact :
                auctionEnforcementPort.cancelForPermanentSuspension(auctionCommand)) {
            insertAuctionImpact(sanction.getSanctionSn(), impact, adminUserSn);
        }

        for (TradeEnforcementImpact trade : tradeRestrictionPort.enforcePermanent(
                new MemberTradeRestrictionCommand(
                        sanction.getUserSn(),
                        adminUserSn,
                        reason,
                        sanction.getSourceReportSn()))) {
            insertPermanentTradeImpact(sanction.getSanctionSn(), trade, adminUserSn);
        }

        MemberServiceRequestEnforcementCommand serviceCommand =
                new MemberServiceRequestEnforcementCommand(
                        sanction.getUserSn(), adminUserSn, reason, null);
        for (ServiceRequestEnforcementImpact impact :
                serviceRequestEnforcementPort.cancelOwnedForPermanentSuspension(serviceCommand)) {
            insertServiceRequestImpact(sanction.getSanctionSn(), impact, adminUserSn);
        }

        for (QuoteEnforcementImpact impact : quoteEnforcementPort.withdrawActiveQuotes(
                sanction.getUserSn(), adminUserSn, reason)) {
            insertQuoteImpact(sanction.getSanctionSn(), impact, adminUserSn);
        }
    }

    private void releaseSanction(
            SanctionRecord sanction,
            Long adminUserSn,
            String reason,
            String requestId,
            boolean automatic) {
        validateRelease(sanction, adminUserSn, reason, requestId);
        ReportSanctionService.ReleaseResult result = reportSanctionService.release(
                new ReportSanctionReleaseCommand(
                        sanction.getSanctionSn(),
                        adminUserSn,
                        reason,
                        requestId,
                        automatic));
        if (!result.changed()) {
            return;
        }

        SanctionRecord released = result.sanction();
        List<SanctionImpactRecord> ownImpacts =
                sanctionImpactMapper.findBySanctionForUpdate(released.getSanctionSn());
        if (reportSanctionService.hasOtherActiveSuspension(
                released.getUserSn(), released.getSanctionSn())) {
            markReleasePending(ownImpacts, String.valueOf(adminUserSn));
        } else {
            restorePending(released.getUserSn(), adminUserSn, reason);
            memberStatusCommandPort.changeStatus(new MemberStatusChangeCommand(
                    released.getUserSn(), ACTIVE_MEMBER, adminUserSn));
        }

        recordAudit(
                "STATUS_CHANGE",
                released.getUserSn(),
                automatic ? null : adminUserSn,
                reason,
                requestId,
                "sanction=" + released.getSanctionSn() + ",released=false",
                "sanction=" + released.getSanctionSn() + ",released=true",
                released.getSourceReportSn());
        notifyReleasedMember(released.getUserSn(), automatic);
    }

    private void insertAuctionImpact(Long sanctionSn, AuctionEnforcementImpact source, Long actor) {
        SanctionImpactRecord impact = baseImpact(
                sanctionSn,
                RefType.AUCTION.getCode(),
                source.auctionId(),
                source.roleCode(),
                source.actionCode(),
                source.previousStatusCode(),
                source.result(),
                actor);
        impact.setPreviousStartAt(source.previousStartAt());
        impact.setPreviousDeadlineAt(source.previousDeadlineAt());
        impact.setRemainingStartSeconds(source.remainingStartSeconds());
        impact.setRemainingSeconds(source.remainingSeconds());
        insertImpact(impact);
    }

    private void insertServiceRequestImpact(
            Long sanctionSn,
            ServiceRequestEnforcementImpact source,
            Long actor) {
        SanctionImpactRecord impact = baseImpact(
                sanctionSn,
                RefType.SERVICE_REQUEST.getCode(),
                source.serviceRequestId(),
                "OWNER",
                source.actionCode(),
                source.previousStatusCode(),
                source.result(),
                actor);
        impact.setPreviousDeadlineAt(source.previousDeadlineAt());
        impact.setRemainingSeconds(source.remainingSeconds());
        insertImpact(impact);
    }

    private void insertQuoteImpact(Long sanctionSn, QuoteEnforcementImpact source, Long actor) {
        insertImpact(baseImpact(
                sanctionSn,
                RefType.QUOTE.getCode(),
                source.quoteId(),
                source.roleCode(),
                source.actionCode(),
                source.previousStatusCode(),
                source.result(),
                actor));
    }

    private void insertTradeImpact(Long sanctionSn, RestrictedTrade source, Long actor) {
        SanctionImpactRecord impact = baseImpact(
                sanctionSn,
                RefType.TRADE.getCode(),
                source.tradeSn(),
                "PARTICIPANT",
                "PAUSED",
                source.previousStatusCode(),
                "계정 제재로 진행 거래와 정산을 보류했습니다.",
                actor);
        impact.setPreviousDeadlineAt(source.previousDeadlineAt());
        impact.setRemainingSeconds(source.remainingSeconds());
        impact.setSettlementHeld(source.settlementHeld());
        insertImpact(impact);
    }

    private void insertPermanentTradeImpact(
            Long sanctionSn,
            TradeEnforcementImpact source,
            Long actor) {
        SanctionImpactRecord impact = baseImpact(
                sanctionSn,
                RefType.TRADE.getCode(),
                source.tradeSn(),
                "PARTICIPANT",
                source.actionCode(),
                source.previousStatusCode(),
                source.result(),
                actor);
        impact.setPreviousDeadlineAt(source.previousDeadlineAt());
        impact.setRemainingSeconds(source.remainingSeconds());
        impact.setSettlementHeld(source.settlementHeld());
        insertImpact(impact);
    }

    private SanctionImpactRecord baseImpact(
            Long sanctionSn,
            String referenceTypeCode,
            Long referenceSn,
            String roleCode,
            String actionCode,
            String previousStatusCode,
            String result,
            Long actor) {
        SanctionImpactRecord impact = new SanctionImpactRecord();
        impact.setSanctionSn(sanctionSn);
        impact.setReferenceTypeCode(referenceTypeCode);
        impact.setReferenceSn(referenceSn);
        impact.setRoleCode(roleCode);
        impact.setActionCode(actionCode);
        impact.setStatusCode(
                Set.of("CANCELED", "BID_CANCELED").contains(actionCode)
                        ? RESOLVED_IMPACT
                        : ACTIVE_IMPACT);
        impact.setPreviousStatusCode(previousStatusCode);
        impact.setResult(result);
        impact.setActorId(String.valueOf(actor));
        return impact;
    }

    private void insertImpact(SanctionImpactRecord impact) {
        inheritOriginalPauseState(impact);
        if (sanctionImpactMapper.insert(impact) != 1) {
            throw new CustomException(ErrorCode.DATABASE_ERROR);
        }
    }

    private void inheritOriginalPauseState(SanctionImpactRecord impact) {
        if (!"PAUSED".equals(impact.getActionCode()) || !isHoldStatus(impact.getPreviousStatusCode())) {
            return;
        }
        sanctionImpactMapper.findUnresolvedByReferenceForUpdate(
                        impact.getReferenceTypeCode(), impact.getReferenceSn())
                .stream()
                .filter(existing -> "PAUSED".equals(existing.getActionCode()))
                .filter(existing -> !isHoldStatus(existing.getPreviousStatusCode()))
                .findFirst()
                .ifPresent(existing -> {
                    impact.setPreviousStatusCode(existing.getPreviousStatusCode());
                    impact.setPreviousStartAt(existing.getPreviousStartAt());
                    impact.setPreviousDeadlineAt(existing.getPreviousDeadlineAt());
                    impact.setRemainingStartSeconds(existing.getRemainingStartSeconds());
                    impact.setRemainingSeconds(existing.getRemainingSeconds());
                    impact.setSettlementHeld(existing.isSettlementHeld());
                });
    }

    private boolean restoreImpact(SanctionImpactRecord impact, Long adminUserSn, String reason) {
        if (RefType.AUCTION.getCode().equals(impact.getReferenceTypeCode())) {
            return auctionEnforcementPort.restore(new AuctionEnforcementRestoreCommand(
                    impact.getReferenceSn(),
                    impact.getPreviousStatusCode(),
                    impact.getRemainingStartSeconds(),
                    impact.getRemainingSeconds(),
                    adminUserSn));
        }
        if (RefType.SERVICE_REQUEST.getCode().equals(impact.getReferenceTypeCode())) {
            return serviceRequestEnforcementPort.restore(new ServiceRequestEnforcementRestoreCommand(
                    impact.getReferenceSn(),
                    impact.getPreviousStatusCode(),
                    impact.getRemainingSeconds(),
                    adminUserSn));
        }
        if (RefType.TRADE.getCode().equals(impact.getReferenceTypeCode())) {
            return tradeRestrictionPort.restoreTrade(new MemberTradeRestoreCommand(
                    impact.getReferenceSn(),
                    impact.getPreviousStatusCode(),
                    impact.getRemainingSeconds(),
                    impact.isSettlementHeld(),
                    adminUserSn,
                    reason));
        }
        return false;
    }

    private SanctionImpactRecord restorableBaseline(List<SanctionImpactRecord> impacts) {
        return impacts.stream()
                .filter(impact -> !isHoldStatus(impact.getPreviousStatusCode()))
                .findFirst()
                .orElse(null);
    }

    private boolean isHoldStatus(String statusCode) {
        return AuctionStatusCode.OPERATION_HOLD.equals(statusCode)
                || "SVCC0005".equals(statusCode)
                || "TRDC0007".equals(statusCode);
    }

    private void markReleasePending(List<SanctionImpactRecord> impacts, String actorId) {
        for (SanctionImpactRecord impact : impacts) {
            if (ACTIVE_IMPACT.equals(impact.getStatusCode())
                    && "PAUSED".equals(impact.getActionCode())) {
                sanctionImpactMapper.updateStatus(
                        impact.getImpactSn(),
                        ACTIVE_IMPACT,
                        RELEASE_PENDING_IMPACT,
                        "다른 활성 계정 제재가 남아 있어 복구를 보류했습니다.",
                        actorId);
            }
        }
    }

    private AdminReportDecisionCommand decisionCommand(
            Long reportSn,
            AdminReportDecision decision,
            String reason,
            Long adminUserSn,
            String requestId) {
        return new AdminReportDecisionCommand(
                reportSn, decision, reason.trim(), String.valueOf(adminUserSn), requestId);
    }

    private void validateDecision(
            Long reportSn,
            AdminReportDecision decision,
            ReportEnforcementAction action,
            String reason,
            Long adminUserSn,
            String requestId) {
        if (reportSn == null || reportSn <= 0
                || decision == null
                || action == null
                || reason == null || reason.isBlank() || reason.trim().length() > 4000
                || adminUserSn == null || adminUserSn <= 0
                || requestId == null || requestId.isBlank() || requestId.length() > 100
                || (decision != AdminReportDecision.PROCESSED
                        && action != ReportEnforcementAction.NONE)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateRelease(
            SanctionRecord sanction,
            Long adminUserSn,
            String reason,
            String requestId) {
        if (sanction == null
                || adminUserSn == null || adminUserSn <= 0
                || reason == null || reason.isBlank() || reason.trim().length() > 1000
                || requestId == null || requestId.isBlank() || requestId.length() > 100) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateRecovery(Long userSn, Long adminUserSn, String reason) {
        if (userSn == null || userSn <= 0
                || adminUserSn == null || adminUserSn <= 0
                || reason == null || reason.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void recordAudit(
            String action,
            Long userSn,
            Long adminUserSn,
            String reason,
            String requestId,
            String before,
            String after,
            Long relatedReportSn) {
        auditLogPort.record(new AuditLogCommand(
                action,
                adminUserSn == null ? null : String.valueOf(adminUserSn),
                "MEMBER",
                userSn,
                reason.trim(),
                before,
                after,
                requestId,
                relatedReportSn == null ? null : "ABUSE_REPORT",
                relatedReportSn));
    }

    private void notifyRestrictedMember(
            Long userSn,
            ReportEnforcementAction action,
            LocalDateTime releaseAt) {
        String content = action == ReportEnforcementAction.TEMPORARY_SUSPENSION_7_DAYS
                ? "신고 검토 결과 계정이 7일간 정지되었습니다. 해제 예정 시각: " + releaseAt
                : "신고 검토 결과 계정 이용이 정지되었습니다.";
        notificationService.notify(
                userSn,
                NotificationType.OPS,
                NotificationDomain.OPS,
                "신고 처리에 따른 계정 제재",
                content,
                RefType.MEMBER,
                userSn);
    }

    private void notifyReleasedMember(Long userSn, boolean automatic) {
        notificationService.notify(
                userSn,
                NotificationType.OPS,
                NotificationDomain.OPS,
                "계정 이용정지 해제",
                automatic
                        ? "7일 이용정지 기간이 끝나 계정 이용이 재개되었습니다."
                        : "관리자가 7일 이용정지를 조기 해제했습니다.",
                RefType.MEMBER,
                userSn);
    }

    private String childRequestId(String requestId, String suffix) {
        String value = requestId + ":" + suffix;
        return value.length() <= 100 ? value : value.substring(0, 100);
    }

    private record ImpactKey(String referenceTypeCode, Long referenceSn) {
    }
}
