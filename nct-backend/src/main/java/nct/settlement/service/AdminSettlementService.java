package nct.settlement.service;

import java.util.List;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.settlement.domain.Settlement;
import nct.settlement.domain.SettlementAdminAction;
import nct.settlement.domain.SettlementStatus;
import nct.settlement.dto.AdminSettlementActionResponse;
import nct.settlement.dto.AdminSettlementDetailResponse;
import nct.settlement.dto.AdminSettlementListItemResponse;
import nct.settlement.dto.AdminSettlementListRequest;
import nct.settlement.dto.AdminSettlementPageResponse;
import nct.settlement.dto.AdminSettlementRecord;
import nct.settlement.exception.SettlementException;
import nct.settlement.mapper.SettlementMapper;

/** F-OPS-009 관리자 정산 조회와 보류·해제를 조립합니다. */
@Service
@RequiredArgsConstructor
public class AdminSettlementService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SUPPORTED_STATUSES = Set.of(
            "STLC0001", "STLC0002", "STLC0003", "STLC0004");
    private static final String HOLD = "HOLD";
    private static final String RELEASE = "RELEASE";

    private final SettlementMapper settlementMapper;
    private final AuditLogPort auditLogPort;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public AdminSettlementPageResponse getPage(AdminSettlementListRequest request) {
        Query query = validateQuery(request);
        long totalItems = settlementMapper.countAdminPage(query.statusCode(), query.keyword());
        List<AdminSettlementListItemResponse> items = totalItems == 0
                ? List.of()
                : settlementMapper.findAdminPage(
                                query.statusCode(), query.keyword(), query.offset(), query.size())
                        .stream()
                        .map(AdminSettlementListItemResponse::from)
                        .toList();
        return AdminSettlementPageResponse.builder()
                .items(items)
                .page(query.page())
                .size(query.size())
                .totalItems(totalItems)
                .totalPages(totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / query.size()))
                .build();
    }

    @Transactional(readOnly = true)
    public AdminSettlementDetailResponse getDetail(Long settlementId) {
        return AdminSettlementDetailResponse.from(requireDetail(settlementId));
    }

    @Transactional
    public AdminSettlementActionResponse hold(
            Long settlementId, String reason, String requestId, Long adminUserId) {
        return changeStatus(
                settlementId,
                SettlementStatus.PENDING,
                SettlementStatus.ON_HOLD,
                HOLD,
                reason,
                requestId,
                adminUserId);
    }

    @Transactional
    public AdminSettlementActionResponse release(
            Long settlementId, String reason, String requestId, Long adminUserId) {
        return changeStatus(
                settlementId,
                SettlementStatus.ON_HOLD,
                SettlementStatus.PENDING,
                RELEASE,
                reason,
                requestId,
                adminUserId);
    }

    private AdminSettlementActionResponse changeStatus(
            Long settlementId,
            SettlementStatus expected,
            SettlementStatus next,
            String actionType,
            String reason,
            String requestId,
            Long adminUserId) {
        Command command = validateCommand(settlementId, reason, requestId, adminUserId);
        Settlement locked = settlementMapper.selectForUpdate(command.settlementId());
        if (locked == null) {
            throw new SettlementException(
                    ErrorCode.SETTLEMENT_NOT_FOUND,
                    "존재하지 않는 정산 건입니다: " + command.settlementId());
        }

        SettlementAdminAction retried = settlementMapper.findAdminActionByRequestIdForUpdate(
                command.requestId());
        if (retried != null) {
            if (!command.settlementId().equals(retried.getSettlementSn())
                    || !actionType.equals(retried.getActionType())) {
                throw new CustomException(ErrorCode.CONFLICT, "다른 정산 처리에 사용된 요청 식별자입니다.");
            }
            return AdminSettlementActionResponse.builder()
                    .settlementId(command.settlementId())
                    .previousStatusCode(retried.getPreviousStatusCode())
                    .currentStatusCode(retried.getNextStatusCode())
                    .changed(false)
                    .build();
        }

        if (!expected.getCode().equals(locked.getStlmStatusCd())) {
            throw new SettlementException(
                    ErrorCode.SETTLEMENT_INVALID_STATUS,
                    "현재 상태에서는 정산 " + actionLabel(actionType) + " 처리를 할 수 없습니다.");
        }

        if (settlementMapper.updateStatusIfExpected(
                command.settlementId(), expected.getCode(), next.getCode(),
                String.valueOf(command.adminUserId())) != 1) {
            throw new SettlementException(
                    ErrorCode.SETTLEMENT_INVALID_STATUS,
                    "정산 상태가 다른 요청에 의해 변경되었습니다: " + command.settlementId());
        }

        SettlementAdminAction action = action(command, expected, next, actionType);
        try {
            if (settlementMapper.insertAdminAction(action) != 1) {
                throw new CustomException(ErrorCode.CONFLICT, "정산 처리 이력을 기록하지 못했습니다.");
            }
        } catch (DuplicateKeyException exception) {
            throw new CustomException(ErrorCode.CONFLICT, "이미 처리된 정산 요청입니다.");
        }

        auditLogPort.record(new AuditLogCommand(
                "STATUS_CHANGE",
                String.valueOf(command.adminUserId()),
                "TRADE",
                locked.getTrdSn(),
                command.reason(),
                "settlement=" + command.settlementId() + ",status=" + expected.getCode(),
                "settlement=" + command.settlementId() + ",status=" + next.getCode(),
                command.requestId()));

        notificationService.notifySettlement(
                locked.getUsrSn(),
                HOLD.equals(actionType) ? "정산 보류" : "정산 보류 해제",
                HOLD.equals(actionType)
                        ? String.format("거래대금 %,dP의 정산이 보류되었습니다. 사유: %s",
                                locked.getStlmAmt(), command.reason())
                        : String.format("거래대금 %,dP의 정산 보류가 해제되어 대기 상태로 전환되었습니다.",
                                locked.getStlmAmt()),
                locked.getTrdSn());

        return AdminSettlementActionResponse.builder()
                .settlementId(command.settlementId())
                .previousStatusCode(expected.getCode())
                .currentStatusCode(next.getCode())
                .changed(true)
                .build();
    }

    private SettlementAdminAction action(
            Command command,
            SettlementStatus expected,
            SettlementStatus next,
            String actionType) {
        SettlementAdminAction action = new SettlementAdminAction();
        action.setSettlementSn(command.settlementId());
        action.setActionType(actionType);
        action.setPreviousStatusCode(expected.getCode());
        action.setNextStatusCode(next.getCode());
        action.setReason(command.reason());
        action.setRequestId(command.requestId());
        action.setProcessorUserSn(command.adminUserId());
        return action;
    }

    private AdminSettlementRecord requireDetail(Long settlementId) {
        if (settlementId == null || settlementId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        AdminSettlementRecord detail = settlementMapper.findAdminDetail(settlementId);
        if (detail == null) {
            throw new SettlementException(
                    ErrorCode.SETTLEMENT_NOT_FOUND,
                    "존재하지 않는 정산 건입니다: " + settlementId);
        }
        return detail;
    }

    private Query validateQuery(AdminSettlementListRequest request) {
        AdminSettlementListRequest source = request == null
                ? new AdminSettlementListRequest()
                : request;
        if (source.getPage() < 1 || source.getSize() < 1 || source.getSize() > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "페이지는 1 이상, 페이지 크기는 1~100이어야 합니다.");
        }
        String statusCode = trimToNull(source.getStatusCode());
        String keyword = trimToNull(source.getKeyword());
        if (statusCode != null && !SUPPORTED_STATUSES.contains(statusCode)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "지원하지 않는 정산 상태입니다.");
        }
        if (keyword != null && keyword.length() > 100) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "검색어는 100자 이하여야 합니다.");
        }
        return new Query(
                statusCode,
                keyword,
                source.getPage(),
                source.getSize(),
                (long) (source.getPage() - 1) * source.getSize());
    }

    private Command validateCommand(
            Long settlementId, String reason, String requestId, Long adminUserId) {
        if (settlementId == null
                || settlementId <= 0
                || adminUserId == null
                || adminUserId <= 0
                || reason == null
                || reason.isBlank()
                || reason.trim().length() > 1000
                || requestId == null
                || requestId.isBlank()
                || requestId.trim().length() > 100) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return new Command(settlementId, reason.trim(), requestId.trim(), adminUserId);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String actionLabel(String actionType) {
        return HOLD.equals(actionType) ? "보류" : "보류 해제";
    }

    private record Query(String statusCode, String keyword, int page, int size, long offset) {
    }

    private record Command(Long settlementId, String reason, String requestId, Long adminUserId) {
    }
}
