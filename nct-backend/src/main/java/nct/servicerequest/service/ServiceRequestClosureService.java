package nct.servicerequest.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.quote.port.ServiceRequestQuoteExpirationPort;

/**
 * 담당자 7 통합 · F-SVC-003: 요청서 마감과 남은 활성 견적 만료를 한 트랜잭션으로 조정합니다.
 * ServiceRequestService와 QuoteService를 직접 상호 참조하지 않아 순환 빈을 만들지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class ServiceRequestClosureService {

    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final ServiceRequestService serviceRequestService;
    private final ServiceRequestQuoteExpirationPort quoteExpirationPort;

    @Transactional
    public void closeByRequester(Long serviceRequestId, Long requesterUserId) {
        if (requesterUserId == null || requesterUserId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        serviceRequestService.closeServiceRequest(serviceRequestId, requesterUserId);
        quoteExpirationPort.expireActiveQuotes(
                serviceRequestId,
                String.valueOf(requesterUserId));
    }

    @Transactional
    public void closeExpired(Long serviceRequestId) {
        if (serviceRequestService.autoCloseExpiredServiceRequest(serviceRequestId)) {
            quoteExpirationPort.expireActiveQuotes(serviceRequestId, SYSTEM_ACTOR);
        }
    }
}
