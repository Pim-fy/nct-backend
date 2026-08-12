package nct.ops.member.service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.abuse.mapper.AbuseReportMapper;
import nct.abuse.dto.AdminAbuseReportResponse;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.member.dto.AdminMemberSource;
import nct.member.mapper.MemberMapper;
import nct.member.dto.AdminMemberIdentityResponse;
import nct.member.port.AdminMemberIdentityReader;
import nct.member.port.MemberStatusChangeCommand;
import nct.member.port.MemberStatusChangeResult;
import nct.member.port.MemberStatusCommandPort;
import nct.notification.domain.NotificationDomain;
import nct.notification.domain.NotificationType;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.member.dto.AdminMemberDetailResponse;
import nct.ops.member.dto.AdminMemberListItemResponse;
import nct.ops.member.dto.AdminMemberPageResponse;
import nct.ops.member.dto.AdminMemberStatusChangeResponse;
import nct.ops.member.port.AccountSanctionCommand;
import nct.ops.member.port.AccountSanctionHistory;
import nct.ops.member.port.AccountSanctionPort;
import nct.ops.member.port.AccountRestrictionRecoveryPort;
import nct.trade.port.MemberTradeRestrictionCommand;
import nct.trade.port.MemberTradeRestrictionPort;
import nct.trade.port.MemberTradeRestrictionResult;
import nct.trade.port.RestrictedTrade;

/**
 * 담당자 7 · F-OPS-002/019/020: 관리자 회원 조회와 계정 제한을 조립합니다.
 * 담당자 5 제재 계약이 없으면 어떤 상태도 바꾸기 전에 503으로 중단해 부분 성공을 막습니다.
 */
@Service
@RequiredArgsConstructor
public class AdminMemberService {

    private static final String ACTIVE = "USRC0001";
    private static final String SUSPENDED = "USRC0002";
    private static final Set<String> SEARCHABLE_STATUSES = Set.of(ACTIVE, SUSPENDED, "USRC0003");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int HISTORY_LIMIT = 50;

    private final MemberMapper memberMapper;
    private final MemberStatusCommandPort memberStatusCommandPort;
    private final AbuseReportMapper abuseReportMapper;
    private final ObjectProvider<AccountSanctionPort> accountSanctionPortProvider;
    @Autowired(required = false)
    private AccountRestrictionRecoveryPort recoveryPort;
    private final MemberTradeRestrictionPort memberTradeRestrictionPort;
    private final AuditLogPort auditLogPort;
    private final NotificationService notificationService;
    private final AdminMemberIdentityReader memberIdentityReader;

    @Transactional(readOnly = true)
    public AdminMemberPageResponse getMembers(String statusCode, String keyword, int page, int size) {
        String normalizedStatus = normalizeStatusFilter(statusCode);
        String normalizedKeyword = normalizeKeyword(keyword);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long offset = (long) (normalizedPage - 1) * normalizedSize;

        List<AdminMemberListItemResponse> items = memberMapper.findAdminMembers(
                        normalizedStatus, normalizedKeyword, offset, normalizedSize)
                .stream()
                .map(AdminMemberListItemResponse::from)
                .toList();
        long totalItems = memberMapper.countAdminMembers(normalizedStatus, normalizedKeyword);
        int totalPages = totalItems == 0 ? 0 : (int) ((totalItems + normalizedSize - 1) / normalizedSize);
        return new AdminMemberPageResponse(
                items, normalizedPage, normalizedSize, totalItems, totalPages, sanctionPort() != null);
    }

    @Transactional(readOnly = true)
    public AdminMemberDetailResponse getMember(Long userSn) {
        validateUserSn(userSn);
        AdminMemberSource source = memberMapper.findAdminMemberById(userSn)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        AccountSanctionPort sanctionPort = sanctionPort();
        List<AccountSanctionHistory> sanctions = sanctionPort == null
                ? Collections.emptyList()
                : sanctionPort.findHistory(userSn, HISTORY_LIMIT);
        List<AdminAbuseReportResponse> reports =
                abuseReportMapper.findReportsByReportedUser(userSn, HISTORY_LIMIT);
        Set<Long> identityUserSns = new LinkedHashSet<>();
        identityUserSns.add(userSn);
        for (AdminAbuseReportResponse report : reports) {
            if (report.getReporterUserSn() != null) identityUserSns.add(report.getReporterUserSn());
            Long processorUserSn = numericUserSn(report.getProcessedBy());
            if (processorUserSn != null) identityUserSns.add(processorUserSn);
        }
        for (AccountSanctionHistory sanction : sanctions) {
            if (sanction.processedBy() != null) identityUserSns.add(sanction.processedBy());
        }
        Map<Long, AdminMemberIdentityResponse> identities =
                memberIdentityReader.findByUserSns(identityUserSns);
        for (AdminAbuseReportResponse report : reports) {
            report.setReporterMember(identities.get(report.getReporterUserSn()));
            report.setReportedMember(identities.get(report.getReportedUserSn()));
            report.setProcessorMember(identities.get(numericUserSn(report.getProcessedBy())));
        }
        return new AdminMemberDetailResponse(
                AdminMemberListItemResponse.from(source),
                reports,
                sanctions,
                identities,
                sanctionPort != null,
                sanctionPort != null);
    }

    @Transactional
    public AdminMemberStatusChangeResponse changeStatus(
            Long userSn,
            String targetStatusCode,
            String reason,
            String requestId,
            Long adminUserSn) {
        validateChange(userSn, targetStatusCode, reason, requestId, adminUserSn);

        // 제공 계약 부재를 행 잠금과 모든 쓰기보다 먼저 검사한다.
        AccountSanctionPort sanctionPort = sanctionPort();
        if (sanctionPort == null) {
            throw new CustomException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "담당자 5의 제재 생성·해제 계약이 아직 연결되지 않았습니다.");
        }
        String normalizedReason = reason.trim();
        AccountSanctionCommand sanctionCommand = new AccountSanctionCommand(
                userSn, adminUserSn, normalizedReason, requestId.trim());

        MemberTradeRestrictionResult tradeResult = new MemberTradeRestrictionResult(List.of(), 0);
        MemberStatusChangeResult statusResult;
        if (SUSPENDED.equals(targetStatusCode)) {
            statusResult = memberStatusCommandPort.changeStatus(
                    new MemberStatusChangeCommand(userSn, targetStatusCode, adminUserSn));
            if (!statusResult.changed()) {
                return new AdminMemberStatusChangeResponse(
                        userSn,
                        statusResult.previousStatusCode(),
                        statusResult.currentStatusCode(),
                        false,
                        0,
                        0);
            }
            sanctionPort.restrict(sanctionCommand);
            tradeResult = memberTradeRestrictionPort.restrictActiveTrades(
                    new MemberTradeRestrictionCommand(userSn, adminUserSn, normalizedReason));
        } else {
            sanctionPort.release(sanctionCommand);
            if (sanctionPort.hasActiveSuspension(userSn)) {
                recordAudit(userSn, adminUserSn, normalizedReason, requestId.trim(),
                        SUSPENDED, SUSPENDED, tradeResult);
                return new AdminMemberStatusChangeResponse(
                        userSn, SUSPENDED, SUSPENDED, false, 0, 0);
            }
            if (recoveryPort != null) {
                recoveryPort.restorePending(userSn, adminUserSn, normalizedReason);
            }
            statusResult = memberStatusCommandPort.changeStatus(
                    new MemberStatusChangeCommand(userSn, targetStatusCode, adminUserSn));
        }

        String previousStatus = statusResult.previousStatusCode();

        recordAudit(userSn, adminUserSn, normalizedReason, requestId.trim(),
                previousStatus, statusResult.currentStatusCode(), tradeResult);
        if (statusResult.changed()) {
            notifyMembers(userSn, statusResult.currentStatusCode(), tradeResult);
        }

        return new AdminMemberStatusChangeResponse(
                userSn,
                previousStatus,
                statusResult.currentStatusCode(),
                statusResult.changed(),
                tradeResult.restrictedTradeCount(),
                tradeResult.heldSettlementCount());
    }

    private void recordAudit(
            Long userSn,
            Long adminUserSn,
            String reason,
            String requestId,
            String previousStatus,
            String targetStatus,
            MemberTradeRestrictionResult tradeResult) {
        auditLogPort.record(new AuditLogCommand(
                "STATUS_CHANGE",
                String.valueOf(adminUserSn),
                "MEMBER",
                userSn,
                reason,
                "memberStatus=" + previousStatus,
                "memberStatus=" + targetStatus
                        + ", restrictedTrades=" + tradeResult.restrictedTradeCount()
                        + ", heldSettlements=" + tradeResult.heldSettlementCount(),
                requestId));
    }

    private void notifyMembers(
            Long userSn, String targetStatus, MemberTradeRestrictionResult tradeResult) {
        boolean suspended = SUSPENDED.equals(targetStatus);
        notificationService.notify(
                userSn,
                NotificationType.OPS,
                NotificationDomain.OPS,
                suspended ? "계정 이용이 제한되었습니다" : "계정 이용 제한이 해제되었습니다",
                suspended
                        ? "운영 정책에 따라 계정 이용이 제한되었습니다. 자세한 내용은 고객센터에 문의해 주세요."
                        : "계정 이용 제한이 해제되었습니다. 보류된 거래는 운영 확인 후 별도로 처리됩니다.",
                RefType.MEMBER,
                userSn);

        for (RestrictedTrade trade : tradeResult.restrictedTrades()) {
            if (trade.counterpartUserSn() == null || trade.counterpartUserSn().equals(userSn)) {
                continue;
            }
            notificationService.notify(
                    trade.counterpartUserSn(),
                    NotificationType.OPS,
                    NotificationDomain.TRADE,
                    "진행 중인 거래가 보류되었습니다",
                    "운영 조치로 거래가 보류되었습니다. 자세한 내용은 고객센터에 문의해 주세요.",
                    RefType.TRADE,
                    trade.tradeSn());
        }
    }

    private AccountSanctionPort sanctionPort() {
        return accountSanctionPortProvider.getIfAvailable();
    }

    private String normalizeStatusFilter(String statusCode) {
        if (statusCode == null || statusCode.isBlank()) {
            return null;
        }
        String normalized = statusCode.trim();
        if (!SEARCHABLE_STATUSES.contains(normalized)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "지원하지 않는 회원 상태입니다.");
        }
        return normalized;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > 100) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "검색어는 100자 이하여야 합니다.");
        }
        return normalized;
    }

    private void validateChange(
            Long userSn,
            String targetStatus,
            String reason,
            String requestId,
            Long adminUserSn) {
        validateUserSn(userSn);
        if ((!ACTIVE.equals(targetStatus) && !SUSPENDED.equals(targetStatus))
                || reason == null
                || reason.isBlank()
                || reason.trim().length() > 500
                || requestId == null
                || requestId.isBlank()
                || requestId.trim().length() > 100
                || adminUserSn == null
                || adminUserSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateUserSn(Long userSn) {
        if (userSn == null || userSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private Long numericUserSn(String value) {
        if (value == null || !value.matches("[0-9]+")) return null;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
