package nct.quote.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.common.domain.RefType;
import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.PageResponse;
import nct.global.security.service.ProviderAccessGuard;
import nct.notification.domain.NotificationDomain;
import nct.notification.domain.NotificationType;
import nct.notification.service.NotificationService;
import nct.quote.domain.Quote;
import nct.quote.domain.QuoteHistory;
import nct.quote.domain.QuotePhoto;
import nct.quote.dto.AdminQuoteListItem;
import nct.quote.dto.AdminQuoteSummary;
import nct.quote.dto.MyQuoteSummaryResponse;
import nct.quote.dto.QuoteAttachmentResponse;
import nct.quote.dto.QuoteCreateResponse;
import nct.quote.dto.QuoteHistoryResponse;
import nct.quote.dto.QuoteResponse;
import nct.quote.dto.QuoteStatusResponse;
import nct.quote.dto.QuoteSubmitRequest;
import nct.quote.dto.QuoteUpdateRequest;
import nct.quote.dto.ReceivedQuoteResponse;
import nct.quote.mapper.QuoteMapper;
import nct.quote.port.AdminQuoteListReader;
import nct.quote.port.AdminQuoteSummaryReader;
import nct.quote.port.QuoteSelectionPort;
import nct.quote.port.SelectedServiceQuoteReader;
import nct.quote.port.ServiceRequestQuoteProviderReader;
import nct.provider.service.ActiveProviderGuard;
import nct.servicerequest.port.ServiceRequestQuoteReader;
import nct.servicerequest.port.ServiceRequestQuoteReader.ServiceRequestQuoteTarget;

@Service
@RequiredArgsConstructor
public class QuoteService implements QuoteSelectionPort, SelectedServiceQuoteReader,
        AdminQuoteSummaryReader, AdminQuoteListReader, ServiceRequestQuoteProviderReader {

    private static final String STATUS_SUBMITTED = "QUTC0001";
    private static final String STATUS_REVISED   = "QUTC0002";
    private static final String STATUS_EXPIRED   = "QUTC0003";
    private static final String STATUS_SELECTED  = "QUTC0004";
    private static final String STATUS_WITHDRAWN = "QUTC0005";
    private static final Set<String> ADMIN_QUOTE_STATUSES = Set.of(
            STATUS_SUBMITTED,
            STATUS_REVISED,
            STATUS_EXPIRED,
            STATUS_SELECTED,
            STATUS_WITHDRAWN);

    private static final int MAX_REVISE_CNT = 3;
    private static final int MAX_ATTACHMENT_COUNT = 5;

    private final QuoteMapper quoteMapper;
    private final ServiceRequestQuoteReader serviceRequestQuoteReader;
    private final ProviderAccessGuard providerAccessGuard;
    private final ActiveProviderGuard activeProviderGuard;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;

    /** F-OPS-021: 관리자 목록과 상세가 사용할 견적 요약을 요청 단위로 일괄 제공합니다. */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, AdminQuoteSummary> findSummaries(List<Long> serviceRequestIds) {
        if (serviceRequestIds == null || serviceRequestIds.isEmpty()) {
            return Map.of();
        }
        if (serviceRequestIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return quoteMapper.findAdminSummaries(serviceRequestIds.stream().distinct().toList())
                .stream()
                .peek(this::validateAdminSummary)
                .collect(Collectors.toUnmodifiableMap(
                        AdminQuoteSummary::getServiceRequestId,
                        Function.identity()));
    }

    /** 담당자 3 견적 조회 계약 / 담당자 7 F-OPS-021 소비: 관리자 상세용 비민감 목록입니다. */
    @Override
    @Transactional(readOnly = true)
    public List<AdminQuoteListItem> findByServiceRequestId(Long serviceRequestId) {
        if (serviceRequestId == null || serviceRequestId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        List<AdminQuoteListItem> items = quoteMapper.findAdminQuoteListItems(serviceRequestId);
        if (items == null) {
            throw invalidAdminQuoteList();
        }
        Set<Long> quoteIds = new HashSet<>();
        for (AdminQuoteListItem item : items) {
            if (item == null
                    || item.getServiceRequestId() == null
                    || !serviceRequestId.equals(item.getServiceRequestId())
                    || item.getQuoteId() == null
                    || item.getQuoteId() <= 0
                    || !quoteIds.add(item.getQuoteId())
                    || item.getProviderUserId() == null
                    || item.getProviderUserId() <= 0
                    || item.getAmount() == null
                    || item.getAmount() <= 0
                    || !ADMIN_QUOTE_STATUSES.contains(item.getStatusCode())
                    || item.getReviseCount() < 0
                    || item.getSubmittedAt() == null
                    || item.getUpdatedAt() == null) {
                throw invalidAdminQuoteList();
            }
        }
        return List.copyOf(items);
    }

    private CustomException invalidAdminQuoteList() {
        return new CustomException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "관리자 견적 목록 데이터가 일관되지 않습니다.");
    }

    private void validateAdminSummary(AdminQuoteSummary summary) {
        if (summary.getServiceRequestId() == null
                || summary.getSelectedQuoteCount() < 0
                || summary.getSelectedQuoteCount() > 1
                || summary.getUnsupportedQuoteCount() != 0
                || summary.getActiveQuoteCount() < 0
                || summary.getTotalQuoteCount() < summary.getActiveQuoteCount()) {
            throw new CustomException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "견적 통합상태 데이터가 일관되지 않습니다.");
        }
        boolean hasSelectedQuote = summary.getSelectedQuoteCount() == 1;
        if (hasSelectedQuote != (summary.getSelectedQuoteId() != null)
                || hasSelectedQuote != STATUS_SELECTED.equals(summary.getSelectedQuoteStatusCode())
                || hasSelectedQuote != (summary.getSelectedProviderUserId() != null)
                || hasSelectedQuote != (summary.getSelectedAmount() != null
                        && summary.getSelectedAmount() > 0)) {
            throw new CustomException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "선택 견적 통합상태 데이터가 일관되지 않습니다.");
        }
    }

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
                .qutTtl(request.title())
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

        savePhotos(quote.getQutSn(), usrSn, request.photoFlSns());

        notificationService.notify(
                target.requesterUsrSn(),
                NotificationType.SERVICE,
                NotificationDomain.SERVICE,
                "새 견적이 도착했습니다",
                "등록하신 서비스 요청에 새 견적이 도착했습니다.",
                RefType.QUOTE,
                quote.getQutSn());
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
        ServiceRequestQuoteTarget target = requireCurrentProviderAccess(usrSn, quote.getSvcReqSn());
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

        quoteMapper.deleteQuotePhotosByQutSn(qutSn);
        savePhotos(qutSn, usrSn, request.photoFlSns());

        notificationService.notify(
                target.requesterUsrSn(),
                NotificationType.SERVICE,
                NotificationDomain.SERVICE,
                "받은 견적이 수정되었습니다",
                "견적이 수정되었습니다",
                RefType.QUOTE,
                qutSn);
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
        requireCurrentProviderAccess(usrSn, quote.getSvcReqSn());
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

    /**
     * 담당자 7 통합, F-PROV-009: 종료·철회된 과거 견적까지 포함하는 본인 이력이다.
     * 특정 카테고리의 신규 업무가 아니므로 활성 권한 하나 이상과 제재 여부를 검증하고,
     * 수정·철회처럼 특정 요청을 변경할 때만 해당 요청 카테고리 권한을 추가 검증한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<QuoteResponse> getMyQuotes(Long usrSn, int page, int size) {
        if (usrSn == null || usrSn <= 0 || page < 1 || size < 1 || size > 50) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        activeProviderGuard.requireActive(usrSn);
        int offset = (page - 1) * size;
        List<QuoteResponse> content = quoteMapper.findMyQuotes(usrSn, offset, size);
        List<Long> svcReqSnList = content.stream()
                .map(QuoteResponse::getSvcReqSn)
                .distinct()
                .toList();
        Map<Long, String> titles = serviceRequestQuoteReader.findTitles(svcReqSnList);
        content.forEach(quote -> {
            quote.setSvcReqTitle(titles.get(quote.getSvcReqSn()));
            populateAttachments(quote);
        });
        int total = quoteMapper.countMyQuotes(usrSn);
        return PageResponse.<QuoteResponse>builder()
                .content(content)
                .totalCount(total)
                .page(page)
                .size(size)
                .hasNext(offset + content.size() < total)
                .build();
    }

    /** 담당자 7 연동 · F-PROV-009: 제공자 대시보드용 활성 견적 수를 반환합니다. */
    @Transactional(readOnly = true)
    public MyQuoteSummaryResponse getMyQuoteSummary(Long usrSn) {
        if (usrSn == null || usrSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        activeProviderGuard.requireActive(usrSn);
        return new MyQuoteSummaryResponse(quoteMapper.countMyActiveQuotes(usrSn));
    }

    /** 제공자가 특정 서비스 요청에 이미 제출한 수정 가능한 견적을 조회한다. */
    @Transactional(readOnly = true)
    public QuoteResponse getMyActiveQuote(Long usrSn, Long svcReqSn) {
        if (usrSn == null || usrSn <= 0 || svcReqSn == null || svcReqSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        requireCurrentProviderAccess(usrSn, svcReqSn);
        QuoteResponse quote = quoteMapper.findMyActiveQuote(usrSn, svcReqSn);
        if (quote != null) {
            populateAttachments(quote);
        }
        return quote;
    }

    /**
     * 담당자 7 통합, F-PROV-011·F-SVC-006·008: 과거 JWT 표시가 아니라 현재 회원 상태,
     * 요청 카테고리 승인 권한과 유효 제재 여부를 서비스 계층에서 다시 검증한다.
     */
    private ServiceRequestQuoteTarget requireCurrentProviderAccess(Long usrSn, Long svcReqSn) {
        ServiceRequestQuoteTarget target = serviceRequestQuoteReader.requireForProviderAccess(svcReqSn);
        activeProviderGuard.requireActiveForCategory(usrSn, target.categorySn());
        return target;
    }

    /** 받은 견적 목록 조회 (요청자용). 본인 서비스 요청에 달린 견적만 허용. */
    @Transactional(readOnly = true)
    public List<ReceivedQuoteResponse> getReceivedQuotes(Long usrSn, Long svcReqSn) {
        if (usrSn == null || usrSn <= 0 || svcReqSn == null || svcReqSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        serviceRequestQuoteReader.requireOwner(svcReqSn, usrSn);
        List<ReceivedQuoteResponse> quotes = quoteMapper.findQuotesBySvcReqSn(svcReqSn);
        quotes.forEach(this::populateAttachments);
        return quotes;
    }

    /**
     * 담당자 7 통합: 견적 작성자 또는 해당 서비스 요청의 의뢰자만 첨부파일을 열람한다.
     * URL에 다른 파일 번호를 넣어도 QUOTE_PHOTO 연결을 먼저 확인해 우회할 수 없다.
     */
    @Transactional(readOnly = true)
    public void requireAttachmentAccess(Long viewerUsrSn, Long qutSn, Long flSn) {
        if (viewerUsrSn == null || viewerUsrSn <= 0 || qutSn == null || qutSn <= 0 || flSn == null || flSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Quote quote = quoteMapper.findQuoteById(qutSn);
        if (quote == null) {
            throw new CustomException(ErrorCode.QUOTE_NOT_FOUND);
        }
        if (quoteMapper.countQuoteAttachment(qutSn, flSn) != 1) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        }
        if (viewerUsrSn.equals(quote.getUsrSn())) {
            return;
        }

        try {
            serviceRequestQuoteReader.requireOwner(quote.getSvcReqSn(), viewerUsrSn);
        } catch (CustomException e) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }
    }

    /**
     * 견적 수정 이력 조회 (F-SVC-007).
     * 견적 작성자(제공자) 또는 해당 서비스 요청 소유자(요청자) 모두 조회 가능.
     */
    @Transactional(readOnly = true)
    public List<QuoteHistoryResponse> getQuoteHistory(Long usrSn, Long qutSn) {
        if (usrSn == null || usrSn <= 0 || qutSn == null || qutSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Quote quote = quoteMapper.findQuoteById(qutSn);
        if (quote == null) {
            throw new CustomException(ErrorCode.QUOTE_NOT_FOUND);
        }

        boolean isProvider = usrSn.equals(quote.getUsrSn());
        if (!isProvider) {
            // 요청자 케이스: 이 견적이 달린 서비스 요청의 소유자인지 확인
            try {
                serviceRequestQuoteReader.requireOwner(quote.getSvcReqSn(), usrSn);
            } catch (CustomException e) {
                throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
            }
        }

        return quoteMapper.findQuoteHistory(qutSn);
    }

    /** 견적 상태 단건 조회 — 담당자4·7 소비용. 견적 제공자 또는 서비스 요청 소유자만 허용. */
    @Transactional(readOnly = true)
    public QuoteStatusResponse getQuoteStatus(Long usrSn, Long qutSn) {
        if (usrSn == null || usrSn <= 0 || qutSn == null || qutSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Quote quote = quoteMapper.findQuoteById(qutSn);
        if (quote == null) {
            throw new CustomException(ErrorCode.QUOTE_NOT_FOUND);
        }
        if (!usrSn.equals(quote.getUsrSn())) {
            try {
                serviceRequestQuoteReader.requireOwner(quote.getSvcReqSn(), usrSn);
            } catch (CustomException e) {
                throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
            }
        }
        return new QuoteStatusResponse(quote.getQutSn(), quote.getQutStatusCd());
    }

    /**
     * QuoteSelectionPort 구현 (F-SVC-009) — 신현석(담당자2) 트랜잭션에서 호출.
     * 선택된 견적 QUTC0004 전이 + 경쟁 견적 QUTC0005 일괄 철회.
     * @Transactional 기본 전파(REQUIRED)로 호출자 트랜잭션에 참여.
     */
    @Override
    @Transactional
    public SelectedQuoteResult selectQuote(Long quoteId, Long svcReqSn, Long requesterUsrSn) {
        if (quoteId == null || quoteId <= 0
                || svcReqSn == null || svcReqSn <= 0
                || requesterUsrSn == null || requesterUsrSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Quote quote = quoteMapper.findQuoteByIdForUpdate(quoteId);
        if (quote == null) {
            throw new CustomException(ErrorCode.QUOTE_NOT_FOUND);
        }
        if (!svcReqSn.equals(quote.getSvcReqSn())) {
            throw new CustomException(ErrorCode.QUOTE_NOT_IN_SERVICE_REQUEST);
        }
        // 요청자 소유권 검증 — ServiceRequestQuoteReader 재사용
        serviceRequestQuoteReader.requireOwner(svcReqSn, requesterUsrSn);

        // 동일 견적 재호출 멱등 처리 — 이미 선택 완료된 견적이면 기존 결과 반환
        if (STATUS_SELECTED.equals(quote.getQutStatusCd())) {
            return new SelectedQuoteResult(quote.getQutSn(), quote.getUsrSn(), quote.getQutAmt());
        }
        if (!STATUS_SUBMITTED.equals(quote.getQutStatusCd())
                && !STATUS_REVISED.equals(quote.getQutStatusCd())) {
            throw new CustomException(ErrorCode.QUOTE_INVALID_STATUS);
        }

        String actorId = String.valueOf(requesterUsrSn);
        int updated = quoteMapper.selectQuote(quoteId, actorId);
        if (updated != 1) {
            throw new CustomException(ErrorCode.DATABASE_ERROR);
        }
        quoteMapper.withdrawCompetingQuotes(svcReqSn, quoteId, actorId);

        return new SelectedQuoteResult(quote.getQutSn(), quote.getUsrSn(), quote.getQutAmt());
    }

    /**
     * SelectedServiceQuoteReader 구현 — 정민재(담당자4) 거래 생성 트랜잭션에서 호출.
     * 이미 QUTC0004인 견적을 FOR UPDATE로 잠그고 검증 후 반환.
     */
    @Override
    @Transactional
    public SelectedServiceQuoteTarget lockSelectedQuoteForTradeCreation(
            Long requesterUserId, Long serviceRequestId, Long quoteId) {

        if (requesterUserId == null || requesterUserId <= 0
                || serviceRequestId == null || serviceRequestId <= 0
                || quoteId == null || quoteId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Quote quote = quoteMapper.findQuoteByIdForUpdate(quoteId);
        if (quote == null) {
            throw new CustomException(ErrorCode.QUOTE_NOT_FOUND);
        }
        if (!serviceRequestId.equals(quote.getSvcReqSn())) {
            throw new CustomException(ErrorCode.QUOTE_NOT_IN_SERVICE_REQUEST);
        }
        serviceRequestQuoteReader.requireOwner(serviceRequestId, requesterUserId);

        if (!STATUS_SELECTED.equals(quote.getQutStatusCd())) {
            throw new CustomException(ErrorCode.QUOTE_NOT_SELECTED);
        }

        return new SelectedServiceQuoteTarget(
                quote.getSvcReqSn(),
                quote.getQutSn(),
                requesterUserId,
                quote.getUsrSn(),
                quote.getQutAmt(),
                quote.getQutStatusCd());
    }

    /** ServiceRequestQuoteProviderReader 구현 — 담당자2(신현석) 요청서 변경 알림 발행 시 소비. */
    @Override
    @Transactional(readOnly = true)
    public List<Long> findProviderUsrSnBySvcReqSn(Long svcReqSn) {
        if (svcReqSn == null || svcReqSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return quoteMapper.findProviderUsrSnBySvcReqSn(svcReqSn);
    }

    private void savePhotos(Long qutSn, Long usrSn, List<Long> photoFlSns) {
        validateAttachmentFileSns(photoFlSns);
        for (int i = 0; i < photoFlSns.size(); i++) {
            fileStorageService.requireOwnedQuoteFile(photoFlSns.get(i), usrSn);
            quoteMapper.insertQuotePhoto(QuotePhoto.builder()
                    .qutSn(qutSn)
                    .flSn(photoFlSns.get(i))
                    .sortNo(i)
                    .build());
        }
    }

    private void validateAttachmentFileSns(List<Long> photoFlSns) {
        if (photoFlSns == null || photoFlSns.isEmpty() || photoFlSns.size() > MAX_ATTACHMENT_COUNT) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Set<Long> distinctFileSns = new HashSet<>(photoFlSns);
        if (distinctFileSns.size() != photoFlSns.size()
                || photoFlSns.stream().anyMatch(flSn -> flSn == null || flSn <= 0)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void populateAttachments(QuoteResponse quote) {
        quote.setAttachments(withAttachmentUrls(quote.getQutSn()));
    }

    private void populateAttachments(ReceivedQuoteResponse quote) {
        quote.setAttachments(withAttachmentUrls(quote.getQutSn()));
    }

    private List<QuoteAttachmentResponse> withAttachmentUrls(Long qutSn) {
        List<QuoteAttachmentResponse> attachments = quoteMapper.findQuoteAttachments(qutSn);
        if (attachments == null) {
            return List.of();
        }
        attachments.forEach(attachment -> attachment.setUrl(
                "/api/quotes/" + qutSn + "/attachments/" + attachment.getFlSn()));
        return attachments;
    }
}
