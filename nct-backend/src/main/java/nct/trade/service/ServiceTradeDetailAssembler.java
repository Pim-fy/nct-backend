package nct.trade.service;

import java.util.ArrayList;
import java.util.List;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.trade.dto.ServiceTradeDetailResponse;
import nct.trade.dto.ServiceTradeDetailSource;

/**
 * 서비스 거래·요청·선택 견적·보관금 조회 결과를 상세 화면용 응답으로 조립한다.
 * 실제 원본 조회는 소유 도메인 계약으로 연결하며, 이 클래스는 조회·표시 정책만 담당한다.
 */
public class ServiceTradeDetailAssembler {

    private static final String IN_PROGRESS = "TRDC0003";
    private static final String WAITING_CONFIRMATION = "TRDC0005";

    public ServiceTradeDetailResponse assemble(ServiceTradeDetailSource source, long viewerUserId) {
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
                source.escrowStatusCode(),
                source.escrowStatusLabel(),
                resolveAvailableActions(source.tradeStatusCode(), viewerRole));
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

    private List<String> resolveAvailableActions(String statusCode, String viewerRole) {
        List<String> actions = new ArrayList<>();
        if (IN_PROGRESS.equals(statusCode)) {
            if ("PROVIDER".equals(viewerRole)) {
                actions.add("REQUEST_COMPLETION");
            }
            // 일정 변경·취소는 입력 검증만 존재하고, 저장·처리 API가 아직 제공되지 않는다.
            // 구현 전에는 호출 불가능한 행동을 상세 응답에 노출하지 않는다.
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
