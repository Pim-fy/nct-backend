package nct.servicerequest.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.notification.service.NotificationService;
import nct.quote.port.QuoteSelectionPort;
import nct.quote.port.QuoteSelectionPort.SelectedQuoteResult;
import nct.provider.service.ActiveProviderGuard;
import nct.servicerequest.domain.ServiceRequest;
import nct.servicerequest.dto.ServiceRequestQuoteSelectionResponse;
import nct.servicerequest.mapper.ServiceRequestMapper;
import nct.trade.dto.ServiceTradeCreateResult;
import nct.trade.service.ServiceTradeCreationCoordinator;

/**
 * 담당자 7 통합, F-SVC-010/013: 견적·거래·보관금·채팅방·요청서 상태를 하나의 트랜잭션으로 조정한다.
 * QUOTE, TRADE, POINT_LEDGER, CHAT_ROOM을 직접 변경하지 않고 각 도메인의 공개 계약만 호출한다.
 */
@Service
@RequiredArgsConstructor
public class ServiceRequestQuoteSelectionService {

    private static final String STATUS_OPEN = "SVCC0002";
    private static final String STATUS_MATCHED = "SVCC0003";

    private final ServiceRequestMapper serviceRequestMapper;
    private final QuoteSelectionPort quoteSelectionPort;
    private final ActiveProviderGuard activeProviderGuard;
    private final ServiceTradeCreationCoordinator serviceTradeCreationCoordinator;
    // 담당자6 BJN, 2026-08-13: 견적 선택 알림 발행용 — 아래 selectQuoteAndCreateTrade 참조
    private final NotificationService notificationService;

    @Transactional
    public ServiceRequestQuoteSelectionResponse selectQuoteAndCreateTrade(
            Long svcReqSn,
            Long quoteId,
            Long requesterUsrSn) {
        validateIdentifiers(svcReqSn, quoteId, requesterUsrSn);

        ServiceRequest request = serviceRequestMapper.findServiceRequestEntityByIdForUpdate(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));
        boolean alreadyMatched = validateSelectableRequest(request, requesterUsrSn);

        SelectedQuoteResult selectedQuote = null;
        if (!alreadyMatched) {
            selectedQuote = quoteSelectionPort.selectQuote(
                    quoteId,
                    svcReqSn,
                    requesterUsrSn);
            if (selectedQuote == null || selectedQuote.providerUsrSn() == null) {
                throw new CustomException(ErrorCode.CONFLICT,
                        "선택 견적의 제공자 정보를 확인할 수 없습니다.");
            }
            activeProviderGuard.requireActiveForCategory(selectedQuote.providerUsrSn(), request.getCatSn());
            if (serviceRequestMapper.markServiceRequestMatched(
                    svcReqSn,
                    requesterUsrSn,
                    String.valueOf(requesterUsrSn)) != 1) {
                throw new CustomException(ErrorCode.CONFLICT,
                        "서비스 요청서 상태가 변경되어 견적 선택을 완료할 수 없습니다.");
            }
        }

        ServiceTradeCreateResult result = serviceTradeCreationCoordinator.create(
                requesterUsrSn,
                svcReqSn,
                quoteId);

        if (alreadyMatched && result.isCreated()) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "매칭 완료 요청서에 연결 거래가 없어 상태 복구가 필요합니다.");
        }
        if (!alreadyMatched && !result.isCreated()) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "공개 요청서에 이미 연결된 거래가 있어 상태 확인이 필요합니다.");
        }

        // 담당자6 BJN, 2026-08-13: 견적 선택 알림 — 거래 생성이 끝난 이 시점에 거래 참조로
        // 발행해야 제공자가 알림에서 서비스 거래 상세로 이동할 수 있다(기존에는 견적 참조로
        // 발행돼 이동 버튼이 안 떴음). 같은 트랜잭션이라 앞 단계가 실패하면 알림도 함께 롤백되고,
        // 매칭 완료 재호출(alreadyMatched) 경로에서는 중복 알림을 만들지 않는다.
        if (!alreadyMatched) {
            notificationService.notifyQuoteSelected(
                    selectedQuote.providerUsrSn(),
                    result.getTradeId(),
                    selectedQuote.amount());
        }
        return new ServiceRequestQuoteSelectionResponse(result.getTradeId());
    }

    private void validateIdentifiers(Long svcReqSn, Long quoteId, Long requesterUsrSn) {
        if (svcReqSn == null || svcReqSn <= 0
                || quoteId == null || quoteId <= 0
                || requesterUsrSn == null || requesterUsrSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private boolean validateSelectableRequest(ServiceRequest request, Long requesterUsrSn) {
        if (!Character.valueOf('Y').equals(request.getSvcReqUseYn())) {
            throw new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND);
        }
        if (!requesterUsrSn.equals(request.getUsrSn())) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        if (STATUS_OPEN.equals(request.getSvcReqStatusCd())) {
            return false;
        }
        if (STATUS_MATCHED.equals(request.getSvcReqStatusCd())) {
            return true;
        }
        throw new CustomException(ErrorCode.CONFLICT,
                "견적을 선택할 수 없는 서비스 요청 상태입니다.");
    }
}
