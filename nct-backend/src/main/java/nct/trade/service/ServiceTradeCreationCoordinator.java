package nct.trade.service;

import java.math.BigDecimal;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.trade.dto.ServiceTradeCreateCommand;
import nct.trade.dto.ServiceTradeCreateResult;
import nct.trade.port.SelectedServiceQuote;
import nct.trade.port.SelectedServiceQuoteReader;
import nct.trade.port.ServiceEscrowCreateCommand;
import nct.trade.port.ServiceEscrowCreator;
import nct.trade.port.ServiceTradeCreator;

/**
 * 견적 선택 확정 뒤 서비스 거래와 보관금을 연결하는 조정 클래스다.
 * 실제 견적·보관금 어댑터 계약이 아직 없으므로 Spring 빈으로 등록하지 않는다.
 * 호출자는 선택 상태 전이까지 포함한 상위 @Transactional 안에서 이 클래스를 사용해야 한다.
 */
@RequiredArgsConstructor
public class ServiceTradeCreationCoordinator {

    private final SelectedServiceQuoteReader selectedServiceQuoteReader;
    private final ServiceTradeCreator serviceTradeCreator;
    private final ServiceEscrowCreator serviceEscrowCreator;

    public ServiceTradeCreateResult create(
            long requesterUserId,
            long serviceRequestId,
            long quoteId) {
        SelectedServiceQuote quote = selectedServiceQuoteReader
                .lockSelectedQuoteForTradeCreation(requesterUserId, serviceRequestId, quoteId);
        validateSelection(requesterUserId, serviceRequestId, quoteId, quote);

        ServiceTradeCreateResult result = serviceTradeCreator.createOrGetServiceTrade(
                new ServiceTradeCreateCommand(
                        quote.requesterUserId(),
                        quote.providerUserId(),
                        quote.serviceRequestId(),
                        quote.quoteId(),
                        quote.quoteAmount()));
        if (result.isCreated()) {
            serviceEscrowCreator.createEscrow(new ServiceEscrowCreateCommand(
                    result.getTradeId(), quote.requesterUserId(), toLongAmount(quote.quoteAmount())));
        }
        return result;
    }

    private void validateSelection(
            long requesterUserId,
            long serviceRequestId,
            long quoteId,
            SelectedServiceQuote quote) {
        if (quote == null
                || quote.requesterUserId() != requesterUserId
                || quote.serviceRequestId() != serviceRequestId
                || quote.quoteId() != quoteId
                || quote.providerUserId() <= 0
                || quote.providerUserId() == requesterUserId
                || quote.quoteAmount() == null
                || quote.quoteAmount().signum() <= 0) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "선택 견적의 서비스 거래 생성 조건을 확인할 수 없습니다.");
        }
    }

    private long toLongAmount(BigDecimal amount) {
        try {
            return amount.longValueExact();
        } catch (ArithmeticException exception) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "선택 견적 금액이 올바르지 않습니다.");
        }
    }
}
