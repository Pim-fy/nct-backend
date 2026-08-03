package nct.quote.service;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.PageResponse;
import nct.global.security.service.ProviderAccessGuard;
import nct.quote.domain.Quote;
import nct.quote.domain.QuoteHistory;
import nct.quote.dto.QuoteCreateResponse;
import nct.quote.dto.QuoteHistoryResponse;
import nct.quote.dto.QuoteResponse;
import nct.quote.dto.QuoteSubmitRequest;
import nct.quote.dto.QuoteUpdateRequest;
import nct.quote.dto.ReceivedQuoteResponse;
import nct.quote.mapper.QuoteMapper;
import nct.servicerequest.port.ServiceRequestQuoteReader;
import nct.servicerequest.port.ServiceRequestQuoteReader.ServiceRequestQuoteTarget;

@Service
@RequiredArgsConstructor
public class QuoteService {

    private static final String STATUS_SUBMITTED = "QUTC0001";
    private static final String STATUS_REVISED   = "QUTC0002";
    private static final String STATUS_SELECTED  = "QUTC0004";
    private static final String STATUS_WITHDRAWN = "QUTC0005";

    private static final int MAX_REVISE_CNT = 3;

    private final QuoteMapper quoteMapper;
    private final ServiceRequestQuoteReader serviceRequestQuoteReader;
    private final ProviderAccessGuard providerAccessGuard;

    /** F-SVC-005: 견적 제출. 자기거래 차단 포함. */
    @Transactional
    public QuoteCreateResponse submitQuote(Authentication authentication, QuoteSubmitRequest request) {
        if (request == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        ServiceRequestQuoteTarget target = serviceRequestQuoteReader.requireOpenForQuote(request.svcReqSn());
        Long usrSn = providerAccessGuard.requireServiceAccess(authentication, target.categorySn());
        if (usrSn.equals(target.requesterUsrSn())) {
            throw new CustomException(ErrorCode.QUOTE_SELF_TRADE);
        }

        String actorId = String.valueOf(usrSn);
        Quote quote = Quote.builder()
                .svcReqSn(request.svcReqSn())
                .usrSn(usrSn)
                .qutAmt(request.amount())
                .qutCn(request.content())
                .qutStatusCd(STATUS_SUBMITTED)
                .qutRegId(actorId)
                .qutUpdtId(actorId)
                .build();

        int inserted = quoteMapper.insertQuote(quote);
        if (inserted != 1 || quote.getQutSn() == null) {
            throw new CustomException(ErrorCode.DATABASE_ERROR);
        }
        return new QuoteCreateResponse(quote.getQutSn());
    }

    /** F-SVC-006: 견적 수정. 3회 초과 차단, 수정 전 값을 QUOTE_HISTORY에 기록. */
    @Transactional
    public void updateQuote(Long usrSn, Long qutSn, QuoteUpdateRequest request) {
        if (usrSn == null || usrSn <= 0 || qutSn == null || qutSn <= 0 || request == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Quote quote = quoteMapper.findQuoteByIdForUpdate(qutSn);
        if (quote == null) {
            throw new CustomException(ErrorCode.QUOTE_NOT_FOUND);
        }
        if (!usrSn.equals(quote.getUsrSn())) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        if (quote.getQutReviseCnt() >= MAX_REVISE_CNT) {
            throw new CustomException(ErrorCode.QUOTE_REVISION_LIMIT_EXCEEDED);
        }
        if (!STATUS_SUBMITTED.equals(quote.getQutStatusCd())
                && !STATUS_REVISED.equals(quote.getQutStatusCd())) {
            throw new CustomException(ErrorCode.QUOTE_INVALID_STATUS);
        }

        String actorId = String.valueOf(usrSn);
        QuoteHistory history = QuoteHistory.builder()
                .qutSn(qutSn)
                .qutHstAmt(quote.getQutAmt())
                .qutHstCn(quote.getQutCn())
                .qutHstRegId(actorId)
                .qutHstUpdtId(actorId)
                .build();
        quoteMapper.insertQuoteHistory(history);

        int updated = quoteMapper.updateQuote(qutSn, request, actorId);
        if (updated != 1) {
            throw new CustomException(ErrorCode.DATABASE_ERROR);
        }
    }

    /** F-SVC-008: 견적 철회. 요청자 선택(QUTC0004) 이후 불가. */
    @Transactional
    public void withdrawQuote(Long usrSn, Long qutSn) {
        if (usrSn == null || usrSn <= 0 || qutSn == null || qutSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Quote quote = quoteMapper.findQuoteByIdForUpdate(qutSn);
        if (quote == null) {
            throw new CustomException(ErrorCode.QUOTE_NOT_FOUND);
        }
        if (!usrSn.equals(quote.getUsrSn())) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        if (STATUS_SELECTED.equals(quote.getQutStatusCd())) {
            throw new CustomException(ErrorCode.QUOTE_ALREADY_SELECTED);
        }
        if (!STATUS_SUBMITTED.equals(quote.getQutStatusCd())
                && !STATUS_REVISED.equals(quote.getQutStatusCd())) {
            throw new CustomException(ErrorCode.QUOTE_INVALID_STATUS);
        }

        int updated = quoteMapper.withdrawQuote(qutSn, String.valueOf(usrSn));
        if (updated != 1) {
            throw new CustomException(ErrorCode.DATABASE_ERROR);
        }
    }

    /** 내 견적 목록 (제공자 본인) */
    @Transactional(readOnly = true)
    public PageResponse<QuoteResponse> getMyQuotes(Long usrSn, int page, int size) {
        if (usrSn == null || usrSn <= 0 || page < 1 || size < 1 || size > 50) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        int offset = (page - 1) * size;
        List<QuoteResponse> content = quoteMapper.findMyQuotes(usrSn, offset, size);
        List<Long> svcReqSnList = content.stream()
                .map(QuoteResponse::getSvcReqSn)
                .distinct()
                .toList();
        Map<Long, String> titles = serviceRequestQuoteReader.findTitles(svcReqSnList);
        content.forEach(quote -> quote.setSvcReqTitle(titles.get(quote.getSvcReqSn())));
        int total = quoteMapper.countMyQuotes(usrSn);
        return PageResponse.<QuoteResponse>builder()
                .content(content)
                .totalCount(total)
                .page(page)
                .size(size)
                .hasNext(offset + content.size() < total)
                .build();
    }

    /** 받은 견적 목록 조회 (요청자용). 본인 서비스 요청에 달린 견적만 허용. */
    @Transactional(readOnly = true)
    public List<ReceivedQuoteResponse> getReceivedQuotes(Long usrSn, Long svcReqSn) {
        if (usrSn == null || usrSn <= 0 || svcReqSn == null || svcReqSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        serviceRequestQuoteReader.requireOwner(svcReqSn, usrSn);
        return quoteMapper.findQuotesBySvcReqSn(svcReqSn);
    }

    /** 견적 수정 이력 조회. 본인 견적만 허용. */
    @Transactional(readOnly = true)
    public List<QuoteHistoryResponse> getQuoteHistory(Long usrSn, Long qutSn) {
        if (usrSn == null || usrSn <= 0 || qutSn == null || qutSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Quote quote = quoteMapper.findQuoteByIdForUpdate(qutSn);
        if (quote == null) {
            throw new CustomException(ErrorCode.QUOTE_NOT_FOUND);
        }
        if (!usrSn.equals(quote.getUsrSn())) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        return quoteMapper.findQuoteHistory(qutSn);
    }
}
