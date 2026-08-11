package nct.trade.service;

import java.util.ArrayList;
import java.util.List;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.trade.dto.ServiceTradeDetailResponse;
import nct.trade.dto.ServiceTradeDetailSource;
import nct.trade.dto.ServiceScheduleHistoryItem;

/**
 * 서비스 거래·요청·선택 견적·보관금 조회 결과를 상세 화면용 응답으로 조립한다.
 * 실제 원본 조회는 소유 도메인 계약으로 연결하며, 이 클래스는 조회·표시 정책만 담당한다.
 */
public class ServiceTradeDetailAssembler {

    private static final String IN_PROGRESS = "TRDC0003";
    private static final String WAITING_CONFIRMATION = "TRDC0005";

    public ServiceTradeDetailResponse assemble(ServiceTradeDetailSource source, long viewerUserId) {
        return assemble(source, viewerUserId, List.of());
    }

    public ServiceTradeDetailResponse assemble(
            ServiceTradeDetailSource source,
            long viewerUserId,
            List<ServiceScheduleHistoryItem> scheduleHistory) {
        return assemble(source, viewerUserId, scheduleHistory, null);
    }

    public ServiceTradeDetailResponse assemble(
            ServiceTradeDetailSource source,
            long viewerUserId,
            List<ServiceScheduleHistoryItem> scheduleHistory,
            String serviceAddressLabel) {
        if (source == null || viewerUserId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "서비스 거래 상세 조회 정보가 올바르지 않습니다.");
        }

        String viewerRole = resolveViewerRole(source, viewerUserId);
        return new ServiceTradeDetailResponse(
                source.tradeId(),
                source.serviceRequestId(),
                viewerRole,
                source.tradeStatusCode(),
                source.tradeAmount(),
                source.autoCompleteAt(),
                source.serviceRequestTitle(),
                source.quoteSummary(),
                source.scheduleLabel(),
                serviceAddressLabel,
                source.escrowStatusCode(),
                source.escrowStatusLabel(),
                source.chatAvailable(),
                List.copyOf(scheduleHistory == null ? List.of() : scheduleHistory),
                resolveAvailableActions(source.tradeStatusCode(), viewerRole,
                        source.cancellationDecisionAvailable()));
    }

    /** 담당자 7 · F-OPS-005: 관리자에게는 원문 주소와 당사자 처리 버튼을 제외한 읽기 전용 상세만 제공합니다. */
    public ServiceTradeDetailResponse assembleForAdmin(
            ServiceTradeDetailSource source,
            List<ServiceScheduleHistoryItem> scheduleHistory) {
        if (source == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "서비스 거래 상세 조회 정보가 올바르지 않습니다.");
        }

        return new ServiceTradeDetailResponse(
                source.tradeId(),
                source.serviceRequestId(),
                "ADMIN",
                source.tradeStatusCode(),
                source.tradeAmount(),
                source.autoCompleteAt(),
                source.serviceRequestTitle(),
                source.quoteSummary(),
                source.scheduleLabel(),
                null,
                source.escrowStatusCode(),
                source.escrowStatusLabel(),
                false,
                List.copyOf(scheduleHistory == null ? List.of() : scheduleHistory),
                List.of());
    }

    private String resolveViewerRole(ServiceTradeDetailSource source, long viewerUserId) {
        if (source.requesterUserId() == viewerUserId) {
            return "REQUESTER";
        }
        if (source.providerUserId() == viewerUserId) {
            return "PROVIDER";
        }
        throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER,
                "서비스 거래 당사자만 상세 정보를 조회할 수 있습니다.");
    }

    private List<String> resolveAvailableActions(
            String statusCode, String viewerRole, boolean cancellationDecisionAvailable) {
        List<String> actions = new ArrayList<>();
        if (IN_PROGRESS.equals(statusCode)) {
            if ("PROVIDER".equals(viewerRole)) {
                actions.add("REQUEST_COMPLETION");
            }
            // F-SVC-016: 진행 중인 거래의 두 당사자는 일정 요청을 남길 수 있다.
            // 요청은 상태·수수료·정산을 바꾸지 않고 상태 이력으로만 기록된다.
            actions.add("REQUEST_SCHEDULE_CHANGE");
            actions.add("REQUEST_SCHEDULE_CANCELLATION");
            if (cancellationDecisionAvailable) {
                actions.add("DECIDE_SCHEDULE_CANCELLATION");
            }
            actions.add("SUBMIT_DISPUTE");
        } else if (WAITING_CONFIRMATION.equals(statusCode)) {
            if ("REQUESTER".equals(viewerRole)) {
                actions.add("CONFIRM_COMPLETION");
            }
            actions.add("SUBMIT_DISPUTE");
        }
        return List.copyOf(actions);
    }
}
