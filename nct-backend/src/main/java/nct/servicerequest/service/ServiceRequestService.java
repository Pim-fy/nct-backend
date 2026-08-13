package nct.servicerequest.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;

import lombok.RequiredArgsConstructor;
import nct.file.service.FileStorageService;
import nct.global.dto.PagedResponse;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.notification.service.NotificationService;
import nct.product.mapper.BannedKeywordMapper;
import nct.quote.mapper.QuoteMapper;
import nct.servicerequest.domain.ServiceRequest;
import nct.servicerequest.domain.ServiceRequestCommentAddedEvent;
import nct.servicerequest.domain.SvcReqComment;
import nct.servicerequest.domain.SvcReqImage;
import nct.servicerequest.domain.SvcReqItem;
import nct.servicerequest.dto.AdminServiceRequestDetail;
import nct.servicerequest.dto.AdminServiceRequestListItem;
import nct.servicerequest.dto.AdminServiceRequestPage;
import nct.servicerequest.dto.AdminServiceRequestSearchCondition;
import nct.servicerequest.dto.ServiceRequestRegisterRequest;
import nct.servicerequest.dto.ServiceRequestResponse;
import nct.servicerequest.dto.SvcReqCommentRequest;
import nct.servicerequest.dto.SvcReqCommentResponse;
import nct.servicerequest.mapper.ServiceRequestMapper;
import nct.servicerequest.mapper.SvcReqCommentMapper;
import nct.servicerequest.mapper.SvcReqImageMapper;
import nct.servicerequest.mapper.SvcReqItemMapper;
import nct.servicerequest.port.AdminServiceRequestReader;
import nct.servicerequest.port.AdminServiceRequestCommandPort;
import nct.servicerequest.port.ServiceRequestQuoteReader;
import nct.servicerequest.service.ServiceRequestFormService.ValidatedSubmission;

@Service
@RequiredArgsConstructor
public class ServiceRequestService implements ServiceRequestQuoteReader, AdminServiceRequestReader,
        AdminServiceRequestCommandPort {

    private final ServiceRequestMapper serviceRequestMapper;
    private final SvcReqItemMapper svcReqItemMapper;
    private final SvcReqImageMapper svcReqImageMapper;
    private final SvcReqCommentMapper svcReqCommentMapper;
    private final ServiceRequestFormService serviceRequestFormService;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;
    private final BannedKeywordMapper bannedKeywordMapper;
    private final QuoteMapper quoteMapper;
    private final NotificationService notificationService;

    // 변경사항 추가는 공개 상태에서만 — 매칭완료 이후로는 요청 내용이 확정된 것으로 보고 막는다
    private static final Set<String> COMMENTABLE_STATUS_CD = Set.of("SVCC0002");
    private static final int MAX_COMMENT_COUNT = 3;

    // 클라이언트가 직접 지정할 수 있는 요청서 상태 — 그 외 내부 전이 상태는 서버만 부여
    private static final Set<String> CLIENT_ALLOWED_STATUS_CD = Set.of("SVCC0001", "SVCC0002");
    private static final Set<String> SERVICE_REQUEST_STATUS_CD =
            Set.of("SVCC0001", "SVCC0002", "SVCC0003", "SVCC0004", "SVCC0005");

    private void validateClientStatusCd(String statusCd) {
        if (!CLIENT_ALLOWED_STATUS_CD.contains(statusCd)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "허용되지 않는 요청서 상태값입니다.");
        }
    }

    // 희망 예산 상한 — 1,000,000,000P (사용자 확정, 260810)
    private static final BigDecimal MAX_BUDGET_AMT = BigDecimal.valueOf(1_000_000_000);

    private void validateBudgetUnderMax(BigDecimal svcReqBdgtAmt) {
        if (svcReqBdgtAmt != null && svcReqBdgtAmt.compareTo(MAX_BUDGET_AMT) > 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "희망 예산은 " + MAX_BUDGET_AMT.toPlainString() + "P 이하로 입력해 주세요.");
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<ServiceRequestResponse> searchServiceRequests(
            String keyword, Long categorySn, Long minBudget, Long maxBudget, String sort, int page, int size) {
        PageHelper.startPage(page, size);
        List<ServiceRequestResponse> list =
                serviceRequestMapper.searchServiceRequests(keyword, categorySn, minBudget, maxBudget, sort);
        return PagedResponse.of(new PageInfo<>(list));
    }

    /** 관리자 도메인에 Mapper를 노출하지 않고 전체 상태의 서비스 요청 목록을 제공한다. */
    @Override
    @Transactional(readOnly = true)
    public AdminServiceRequestPage readPage(AdminServiceRequestSearchCondition condition) {
        if (condition == null || condition.getPage() <= 0 || condition.getSize() <= 0
                || (condition.getStatusCode() != null
                    && !SERVICE_REQUEST_STATUS_CD.contains(condition.getStatusCode()))) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        long totalItems = serviceRequestMapper.countAdminServiceRequests(condition);
        List<AdminServiceRequestListItem> items = totalItems == 0
                ? List.of()
                : serviceRequestMapper.findAdminServiceRequestPage(condition);
        return AdminServiceRequestPage.builder()
                .items(items)
                .page(condition.getPage())
                .size(condition.getSize())
                .totalItems(totalItems)
                .totalPages((int) Math.ceil((double) totalItems / condition.getSize()))
                .build();
    }

    /** 관리자 서비스 요청 상세는 논리 삭제되지 않은 서비스요청 소유 필드만 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public AdminServiceRequestDetail readDetail(Long serviceRequestId) {
        if (serviceRequestId == null || serviceRequestId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return serviceRequestMapper.findAdminServiceRequestDetail(serviceRequestId)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));
    }

    /** 담당자 7 소비 계약: 노출 여부와 무관하게 매칭 전 공개 요청을 취소하고 이미 취소된 요청은 멱등 반환합니다. */
    @Override
    @Transactional
    public AdminServiceRequestCommandResult cancelOpen(Long serviceRequestId, Long actorUserId) {
        if (serviceRequestId == null || serviceRequestId <= 0
                || actorUserId == null || actorUserId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        ServiceRequest request = serviceRequestMapper.findServiceRequestEntityByIdForUpdate(serviceRequestId)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));
        if ("SVCC0004".equals(request.getSvcReqStatusCd())) {
            return new AdminServiceRequestCommandResult(
                    serviceRequestId,
                    request.getUsrSn(),
                    "SVCC0004",
                    "SVCC0004",
                    false);
        }
        if (!"SVCC0002".equals(request.getSvcReqStatusCd())) {
            throw new CustomException(
                    ErrorCode.CONFLICT,
                    "매칭 전 공개 상태의 견적 요청만 관리자 취소할 수 있습니다.");
        }
        if (serviceRequestMapper.adminCancelOpenServiceRequest(
                serviceRequestId, String.valueOf(actorUserId)) != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "견적 요청 상태가 이미 변경되었습니다.");
        }
        return new AdminServiceRequestCommandResult(
                serviceRequestId,
                request.getUsrSn(),
                request.getSvcReqStatusCd(),
                "SVCC0004",
                true);
    }

    /** 담당자 7 소비 계약: 매칭 전 공개 요청을 숨김·복구하며 요청과 견적 이력은 보존합니다. */
    @Override
    @Transactional
    public AdminServiceRequestVisibilityResult changeVisibility(
            Long serviceRequestId, boolean visible, Long actorUserId) {
        if (serviceRequestId == null || serviceRequestId <= 0
                || actorUserId == null || actorUserId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        ServiceRequest request = serviceRequestMapper.findServiceRequestEntityByIdForUpdate(serviceRequestId)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));
        if (!"SVCC0002".equals(request.getSvcReqStatusCd())) {
            throw new CustomException(
                    ErrorCode.CONFLICT,
                    "거래가 생성되지 않은 공개 견적 요청만 숨김 상태를 변경할 수 있습니다.");
        }

        boolean previousVisible = Character.valueOf('Y').equals(request.getSvcReqUseYn());
        if (previousVisible == visible) {
            return new AdminServiceRequestVisibilityResult(
                    serviceRequestId,
                    request.getUsrSn(),
                    previousVisible,
                    visible,
                    false);
        }
        String expectedUseYn = previousVisible ? "Y" : "N";
        String targetUseYn = visible ? "Y" : "N";
        if (serviceRequestMapper.updateAdminServiceRequestVisibility(
                serviceRequestId,
                expectedUseYn,
                targetUseYn,
                String.valueOf(actorUserId)) != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "견적 요청 노출 상태가 이미 변경되었습니다.");
        }
        return new AdminServiceRequestVisibilityResult(
                serviceRequestId,
                request.getUsrSn(),
                previousVisible,
                visible,
                true);
    }

    @Transactional
    public ServiceRequestResponse registerServiceRequest(Long usrSn, ServiceRequestRegisterRequest req) {
        String statusCd = (req.getSvcReqStatusCd() != null) ? req.getSvcReqStatusCd() : "SVCC0002";
        validateClientStatusCd(statusCd);
        validateBudgetUnderMax(req.getSvcReqBdgtAmt());
        ValidatedSubmission formSubmission = serviceRequestFormService.validateSubmission(
                req.getCatSn(),
                req.getFormTemplateSn(),
                null,
                req.getStructuredAnswers(),
                req.getAddressList(),
                "SVCC0002".equals(statusCd));
        ServiceRequest serviceRequest = ServiceRequest.builder()
                .usrSn(usrSn)
                .catSn(req.getCatSn())
                .formTemplateSn(formSubmission == null ? null : formSubmission.getFormTemplateSn())
                .svcReqTtl(req.getSvcReqTtl())
                .svcReqCn(req.getSvcReqCn())
                .svcReqBdgtAmt(req.getSvcReqBdgtAmt())
                .svcReqStatusCd(statusCd)
                .svcReqRegId(String.valueOf(usrSn))
                .svcReqUpdtId(String.valueOf(usrSn))
                .build();

        serviceRequestMapper.saveServiceRequest(serviceRequest);
        if (formSubmission != null) {
            serviceRequestFormService.insertSubmission(
                    serviceRequest.getSvcReqSn(),
                    String.valueOf(usrSn),
                    formSubmission);
        } else {
            saveItems(serviceRequest.getSvcReqSn(), req.getItems());
        }
        if ("SVCC0002".equals(statusCd)) {
            serviceRequestMapper.refreshOpenDeadline(serviceRequest.getSvcReqSn());
        }
        saveImages(serviceRequest.getSvcReqSn(), usrSn, req.getFlSnList());

        return serviceRequestMapper.findServiceRequestById(serviceRequest.getSvcReqSn())
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Transactional
    public ServiceRequestResponse updateServiceRequest(Long svcReqSn, Long usrSn, ServiceRequestRegisterRequest req) {
        ServiceRequest existing = serviceRequestMapper.findServiceRequestEntityByIdForUpdate(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));

        if (!existing.getUsrSn().equals(usrSn)) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        if (!"SVCC0001".equals(existing.getSvcReqStatusCd())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "임시저장 상태의 요청서만 수정할 수 있습니다.");
        }

        String statusCd = (req.getSvcReqStatusCd() != null) ? req.getSvcReqStatusCd() : "SVCC0002";
        validateClientStatusCd(statusCd);
        validateBudgetUnderMax(req.getSvcReqBdgtAmt());
        ValidatedSubmission formSubmission = serviceRequestFormService.validateSubmission(
                req.getCatSn(),
                req.getFormTemplateSn(),
                existing.getFormTemplateSn(),
                req.getStructuredAnswers(),
                req.getAddressList(),
                "SVCC0002".equals(statusCd));
        if (formSubmission == null
                && existing.getFormTemplateSn() != null
                && !existing.getCatSn().equals(req.getCatSn())) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "카테고리를 변경하려면 현재 서비스 요청 폼 답변을 함께 제출해야 합니다.");
        }
        if (formSubmission == null
                && existing.getFormTemplateSn() != null
                && req.getItems() != null) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "구조화된 서비스 요청 답변은 기존 문자열 항목으로 교체할 수 없습니다.");
        }
        ServiceRequest updated = ServiceRequest.builder()
                .svcReqSn(svcReqSn)
                .usrSn(usrSn)
                .catSn(req.getCatSn())
                .formTemplateSn(formSubmission == null
                        ? existing.getFormTemplateSn()
                        : formSubmission.getFormTemplateSn())
                .svcReqTtl(req.getSvcReqTtl())
                .svcReqCn(req.getSvcReqCn())
                .svcReqBdgtAmt(req.getSvcReqBdgtAmt())
                .svcReqStatusCd(statusCd)
                .svcReqUpdtId(String.valueOf(usrSn))
                .build();

        // 담당자 7: 템플릿 변경 시 자식 FK가 기존 템플릿을 참조하므로 부모 갱신 전에 정리한다.
        // 이후 단계가 실패하면 메서드 전체 트랜잭션이 롤백되어 기존 답변도 함께 복원된다.
        if (formSubmission != null) {
            serviceRequestFormService.deleteSubmission(svcReqSn);
        }
        int updatedCount = serviceRequestMapper.updateServiceRequest(updated);
        if (updatedCount == 0) {
            throw new CustomException(ErrorCode.CONFLICT, "요청서 상태가 이미 변경되었습니다.");
        }

        if (formSubmission != null) {
            serviceRequestFormService.insertSubmission(svcReqSn, String.valueOf(usrSn), formSubmission);
        } else if (req.getItems() != null) {
            svcReqItemMapper.deleteBySvcReqSn(svcReqSn);
            saveItems(svcReqSn, req.getItems());
        }
        if ("SVCC0002".equals(statusCd)) {
            serviceRequestMapper.refreshOpenDeadline(svcReqSn);
        }

        if (req.getFlSnList() != null) {
            svcReqImageMapper.deleteBySvcReqSn(svcReqSn);
            saveImages(svcReqSn, usrSn, req.getFlSnList());
        }

        return serviceRequestMapper.findServiceRequestById(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Transactional(readOnly = true)
    public ServiceRequestResponse getServiceRequest(
            Long svcReqSn, Long viewerUsrSn, boolean providerViewer) {
        ServiceRequest existing = serviceRequestMapper.findServiceRequestEntityById(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));
        boolean owner = viewerUsrSn != null && existing.getUsrSn().equals(viewerUsrSn);
        if (owner && !providerViewer) {
            return Character.valueOf('N').equals(existing.getSvcReqUseYn())
                    ? buildOwnerHistoryResponse(existing)
                    : buildOwnerResponse(existing);
        }

        // 담당자 7 통합: 일반회원은 URL을 직접 입력해도 다른 회원의 요청을 볼 수 없다.
        if (!providerViewer) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        // 담당자 7: 정본 F-SVC-003에 따라 임시저장은 존재 여부까지 외부에 노출하지 않는다.
        ServiceRequestResponse response = serviceRequestMapper.findPublicServiceRequestById(svcReqSn)
                .orElseGet(() -> findProviderQuoteHistory(svcReqSn, viewerUsrSn));
        response.setItems(svcReqItemMapper.findPublicItemContentsBySvcReqSn(svcReqSn));
        response.setImageList(svcReqImageMapper.findImagesBySvcReqSn(svcReqSn));
        return response;
    }

    /**
     * F-SVC-005: 견적 INSERT와 같은 트랜잭션에서 요청 행을 잠그고 공개 상태인지 확인한다.
     * 마감과 견적 제출이 동시에 실행돼도 마감 뒤 신규 견적이 들어가지 않게 한다.
     */
    @Override
    @Transactional
    public ServiceRequestQuoteTarget requireOpenForQuote(Long svcReqSn) {
        if (svcReqSn == null || svcReqSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        ServiceRequest existing = serviceRequestMapper.findServiceRequestEntityByIdForUpdate(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));
        if (!Character.valueOf('Y').equals(existing.getSvcReqUseYn())
                || !"SVCC0002".equals(existing.getSvcReqStatusCd())) {
            throw new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND);
        }
        if (serviceRequestMapper.countOpenQuoteSubmissionWindow(svcReqSn) != 1) {
            throw new CustomException(
                    ErrorCode.CONFLICT,
                    "견적 접수가 마감된 서비스 요청입니다.");
        }
        return new ServiceRequestQuoteTarget(existing.getUsrSn(), existing.getCatSn());
    }

    /**
     * 담당자 7 통합, F-SVC-006: 견적 수정·활성 견적 조회에서 현재 카테고리 권한을
     * 다시 확인할 수 있도록 요청자와 카테고리를 읽기 전용으로 제공한다.
     * 요청 상태는 견적 상태 검증과 별개이므로 여기서는 사용 여부만 확인한다.
     */
    @Override
    @Transactional(readOnly = true)
    public ServiceRequestQuoteTarget requireForProviderAccess(Long svcReqSn) {
        if (svcReqSn == null || svcReqSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        ServiceRequest existing = serviceRequestMapper.findServiceRequestEntityById(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));
        if (!Character.valueOf('Y').equals(existing.getSvcReqUseYn())) {
            throw new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND);
        }
        return new ServiceRequestQuoteTarget(existing.getUsrSn(), existing.getCatSn());
    }

    /** 견적 도메인의 받은 견적 조회용 소유권 계약. */
    @Override
    @Transactional(readOnly = true)
    public void requireOwner(Long svcReqSn, Long usrSn) {
        if (svcReqSn == null || svcReqSn <= 0 || usrSn == null || usrSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        ServiceRequest existing = serviceRequestMapper.findServiceRequestEntityById(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));
        if (!Character.valueOf('Y').equals(existing.getSvcReqUseYn())) {
            throw new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND);
        }
        if (!existing.getUsrSn().equals(usrSn)) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }
    }

    /** 제공자 내 견적 목록에 필요한 요청 제목만 일괄 제공한다. */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> findTitles(List<Long> svcReqSnList) {
        if (svcReqSnList == null || svcReqSnList.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> titles = new HashMap<>();
        serviceRequestMapper.findServiceRequestTitles(svcReqSnList).forEach(response ->
                titles.put(response.getSvcReqSn(), response.getSvcReqTtl()));
        return titles;
    }

    /** 요청서 첨부사진 보호 API가 상세 조회와 같은 역할·소유권 규칙을 재사용한다. */
    @Transactional(readOnly = true)
    public void requireImageAccess(Long svcReqSn, Long flSn, Long viewerUsrSn, boolean providerViewer) {
        if (svcReqSn == null || svcReqSn <= 0 || flSn == null || flSn <= 0
                || viewerUsrSn == null || viewerUsrSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        ServiceRequest existing = serviceRequestMapper.findServiceRequestEntityById(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));
        boolean owner = existing.getUsrSn().equals(viewerUsrSn);
        if (providerViewer) {
            if (serviceRequestMapper.findPublicServiceRequestById(svcReqSn).isEmpty()) {
                requireProviderQuoteHistory(svcReqSn, viewerUsrSn);
            }
        } else if (!owner) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        if (svcReqImageMapper.countImageLink(svcReqSn, flSn) == 0) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        }
    }

    /** 요청자 수정 화면 전용 상세. 정확주소와 비공개 답변은 소유권 확인 후에만 반환한다. */
    @Transactional(readOnly = true)
    public ServiceRequestResponse getEditableServiceRequest(Long svcReqSn, Long usrSn) {
        ServiceRequest existing = serviceRequestMapper.findServiceRequestEntityById(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));
        if (!existing.getUsrSn().equals(usrSn)) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }

        return buildOwnerResponse(existing);
    }

    private ServiceRequestResponse buildOwnerResponse(ServiceRequest existing) {
        Long svcReqSn = existing.getSvcReqSn();
        ServiceRequestResponse response = serviceRequestMapper.findServiceRequestById(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));
        response.setItems(svcReqItemMapper.findAllItemContentsBySvcReqSn(svcReqSn));
        response.setImageList(svcReqImageMapper.findImagesBySvcReqSn(svcReqSn));
        if (existing.getFormTemplateSn() != null) {
            response.setStructuredAnswers(serviceRequestFormService.getOwnerAnswers(svcReqSn));
            response.setAddressList(serviceRequestFormService.getOwnerAddresses(svcReqSn));
        }
        return response;
    }

    private ServiceRequestResponse buildOwnerHistoryResponse(ServiceRequest existing) {
        Long svcReqSn = existing.getSvcReqSn();
        ServiceRequestResponse response = serviceRequestMapper.findServiceRequestHistoryById(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));
        response.setItems(svcReqItemMapper.findAllItemContentsBySvcReqSn(svcReqSn));
        response.setImageList(svcReqImageMapper.findImagesBySvcReqSn(svcReqSn));
        if (existing.getFormTemplateSn() != null) {
            response.setStructuredAnswers(serviceRequestFormService.getOwnerAnswers(svcReqSn));
            response.setAddressList(serviceRequestFormService.getOwnerAddresses(svcReqSn));
        }
        return response;
    }

    private ServiceRequestResponse findProviderQuoteHistory(Long svcReqSn, Long viewerUsrSn) {
        requireProviderQuoteHistory(svcReqSn, viewerUsrSn);
        return serviceRequestMapper.findServiceRequestHistoryById(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));
    }

    private void requireProviderQuoteHistory(Long svcReqSn, Long viewerUsrSn) {
        if (viewerUsrSn == null
                || viewerUsrSn <= 0
                || quoteMapper.countQuotesByRequestAndProvider(svcReqSn, viewerUsrSn) == 0) {
            throw new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND);
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<ServiceRequestResponse> getMyServiceRequests(Long usrSn, int page, int size, String filterType) {
        PageHelper.startPage(page, size);
        List<ServiceRequestResponse> list = serviceRequestMapper.findMyServiceRequests(usrSn, filterType);
        return PagedResponse.of(new PageInfo<>(list));
    }

    /** 요청서 마감 — 공개(SVCC0002) 상태에서만 종료(SVCC0004)로 전환 가능 (F-SVC-003) */
    @Transactional
    public void closeServiceRequest(Long svcReqSn, Long usrSn) {
        ServiceRequest serviceRequest = serviceRequestMapper.findServiceRequestEntityByIdForUpdate(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));

        if (!serviceRequest.getUsrSn().equals(usrSn)) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        if (!"SVCC0002".equals(serviceRequest.getSvcReqStatusCd())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "공개 상태의 요청서만 마감할 수 있습니다.");
        }

        int updated = serviceRequestMapper.closeServiceRequest(svcReqSn, usrSn, String.valueOf(usrSn));
        if (updated == 0) {
            throw new CustomException(ErrorCode.CONFLICT, "요청서 상태가 이미 변경되었습니다.");
        }
        notifyActiveQuoteProviders(svcReqSn);
    }

    /** 견적 요청 기간 만료 대상 조회 (F-SVC-003, 배치 전용) */
    @Transactional(readOnly = true)
    public List<Long> findExpiredOpenServiceRequestIds(int limit) {
        return serviceRequestMapper.findExpiredOpenServiceRequestIds(limit);
    }

    /** 견적 요청 기간 만료 자동 마감 — 실제 상태 전이 여부를 종료 오케스트레이터에 반환한다. */
    @Transactional
    public boolean autoCloseExpiredServiceRequest(Long svcReqSn) {
        boolean closed = serviceRequestMapper.autoCloseServiceRequest(svcReqSn) == 1;
        if (closed) {
            notifyActiveQuoteProviders(svcReqSn);
        }
        return closed;
    }

    // 마감 시점에 아직 선택되지 않은 활성 견적(제출됨·수정됨)의 제공자 전원에게 마감 알림을 보낸다.
    // 수동/자동 마감 두 경로가 공유한다 (황성경 제보, 2026-08-13). 조우진의 ServiceRequestClosureService가
    // 이 메서드 다음에 견적 만료(quoteExpirationPort.expireActiveQuotes)를 호출하므로, 견적이 아직
    // 활성 상태인 이 시점에 알림을 먼저 보내야 조회 대상이 비어있지 않다.
    private void notifyActiveQuoteProviders(Long svcReqSn) {
        for (Long providerUsrSn : quoteMapper.findActiveQuoteProvidersBySvcReqSn(svcReqSn)) {
            notificationService.notifyServiceRequestClosed(providerUsrSn, svcReqSn);
        }
    }

    /** 마감 후 1일 경과 요청서 조회 (자동 삭제 배치 전용) */
    @Transactional(readOnly = true)
    public List<Long> findExpiredClosedServiceRequestIds(LocalDateTime cutoff, int limit) {
        return serviceRequestMapper.findExpiredClosedServiceRequestIds(cutoff, limit);
    }

    /** 마감 후 1일 경과 요청서 자동 삭제 — ServiceRequestClosedAutoDeleteScheduler 전용, 소유자 확인 없이 시스템이 직접 처리 */
    @Transactional
    public void deleteExpiredClosedServiceRequest(Long svcReqSn) {
        serviceRequestMapper.deleteExpiredClosedServiceRequest(svcReqSn);
    }

    // 마감(SVCC0004)된 요청서 재등록 — 원본은 이력으로 그대로 남기고, 내용을 복사한 새 요청서(임시저장)를
    // 별도 svcReqSn으로 만든다. 같은 번호를 재사용하면 옛 견적·변경사항 기록이 새 라운드와 뒤섞이게 된다.
    @Transactional
    public ServiceRequestResponse reregisterServiceRequest(Long svcReqSn, Long usrSn) {
        ServiceRequest original = serviceRequestMapper.findServiceRequestEntityById(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));

        if (!original.getUsrSn().equals(usrSn)) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        if (!"SVCC0004".equals(original.getSvcReqStatusCd())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "마감된 요청서만 재등록할 수 있습니다.");
        }

        ServiceRequest copy = ServiceRequest.builder()
                .usrSn(usrSn)
                .catSn(original.getCatSn())
                .formTemplateSn(original.getFormTemplateSn())
                .svcReqTtl(original.getSvcReqTtl())
                .svcReqCn(original.getSvcReqCn())
                .svcReqBdgtAmt(original.getSvcReqBdgtAmt())
                .svcReqStatusCd("SVCC0001")
                .svcReqRegId(String.valueOf(usrSn))
                .svcReqUpdtId(String.valueOf(usrSn))
                .build();
        serviceRequestMapper.saveServiceRequest(copy);

        List<SvcReqItem> items = svcReqItemMapper.findItemEntitiesBySvcReqSn(svcReqSn);
        if (!items.isEmpty()) {
            List<SvcReqItem> copiedItems = items.stream()
                    .map(item -> item.toBuilder().svcReqItmSn(null).svcReqSn(copy.getSvcReqSn()).build())
                    .toList();
            svcReqItemMapper.insertAll(copiedItems);
        }

        List<SvcReqImage> images = svcReqImageMapper.findImageEntitiesBySvcReqSn(svcReqSn);
        if (!images.isEmpty()) {
            List<SvcReqImage> copiedImages = images.stream()
                    .map(image -> image.toBuilder().svcReqImgSn(null).svcReqSn(copy.getSvcReqSn()).build())
                    .toList();
            svcReqImageMapper.insertAll(copiedImages);
        }

        return serviceRequestMapper.findServiceRequestById(copy.getSvcReqSn())
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    // 삭제는 임시저장 상태만 허용 — 공개 이후에는 제공자가 이미 견적을 냈을 수 있어 셀프 삭제로
    // 기록을 지우면 안 되고, 더 이상 견적을 받고 싶지 않을 땐 마감(closeServiceRequest)을 쓴다.
    @Transactional
    public void deleteServiceRequest(Long svcReqSn, Long usrSn) {
        ServiceRequest serviceRequest = serviceRequestMapper.findServiceRequestEntityById(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));

        if (!serviceRequest.getUsrSn().equals(usrSn)) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        if (!"SVCC0001".equals(serviceRequest.getSvcReqStatusCd())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "임시저장 상태의 요청서만 삭제할 수 있습니다.");
        }

        serviceRequestMapper.deleteServiceRequest(svcReqSn, usrSn);
    }

    /** 요청서 변경사항 추가 — 등록 후 본문 수정은 불가하므로 별도 이력으로 최대 3개까지 (견적 요청 정책) */
    @Transactional
    public SvcReqCommentResponse addComment(Long svcReqSn, Long usrSn, SvcReqCommentRequest req) {
        ServiceRequest serviceRequest = serviceRequestMapper.findServiceRequestEntityById(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));

        if (!serviceRequest.getUsrSn().equals(usrSn)) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        if (!COMMENTABLE_STATUS_CD.contains(serviceRequest.getSvcReqStatusCd())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "공개 상태의 요청서에만 변경사항을 추가할 수 있습니다.");
        }
        if (svcReqCommentMapper.findLatestComments(svcReqSn, Integer.MAX_VALUE).size() >= MAX_COMMENT_COUNT) {
            throw new CustomException(ErrorCode.CONFLICT, "변경사항은 최대 " + MAX_COMMENT_COUNT + "개까지 등록할 수 있습니다.");
        }

        // 상품 변경사항(F-AUC-007)과 동일한 PRODUCT_BANNED_KEYWORD 목록으로 검사한다 —
        // 공개 상세에 그대로 노출되는 자유 텍스트라 같은 우회 경로가 열려있다.
        List<String> bannedKeywords = bannedKeywordMapper.findActiveBannedKeywords();
        checkBannedKeyword(req.getTtl(), "변경사항 제목", bannedKeywords);
        checkBannedKeyword(req.getCn(), "변경사항 내용", bannedKeywords);

        SvcReqComment comment = SvcReqComment.builder()
                .svcReqSn(svcReqSn)
                .usrSn(usrSn)
                .svcReqCmtTtl(req.getTtl())
                .svcReqCmtCn(req.getCn())
                .svcReqCmtRegId(String.valueOf(usrSn))
                .build();
        svcReqCommentMapper.insertComment(comment);
        // 요청서 변경사항(F-SVC-006) 커밋 후 견적 제출 제공자 알림 — quote 도메인을 직접 참조하면
        // QuoteService↔ServiceRequestService 순환참조가 되므로 이벤트로 발행해 방향을 끊는다
        // (AdminDisputeDecisionCommittedEvent와 동일한 패턴, 리스너는 nct.quote.service에 있다)
        eventPublisher.publishEvent(new ServiceRequestCommentAddedEvent(svcReqSn, serviceRequest.getSvcReqTtl(), req.getTtl()));

        return svcReqCommentMapper.findLatestComments(svcReqSn, MAX_COMMENT_COUNT).stream()
                .filter(c -> c.getSvcReqCmtSn().equals(comment.getSvcReqCmtSn()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    /** 요청서 변경사항 목록 조회 — 최신순 최대 3개 */
    @Transactional(readOnly = true)
    public List<SvcReqCommentResponse> getComments(Long svcReqSn) {
        serviceRequestMapper.findServiceRequestEntityById(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));
        return svcReqCommentMapper.findLatestComments(svcReqSn, MAX_COMMENT_COUNT);
    }

    private void checkBannedKeyword(String text, String fieldLabel, List<String> keywords) {
        if (text == null) return;
        String lower = text.toLowerCase();
        keywords.stream()
                .filter(kwd -> lower.contains(kwd.toLowerCase()))
                .findFirst()
                .ifPresent(kwd -> {
                    throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                            "'" + kwd + "'은(는) 등록할 수 없는 " + fieldLabel + "입니다.");
                });
    }

    // 요청 항목 목록을 순서대로 SVC_REQ_ITEM에 저장
    private void saveItems(Long svcReqSn, List<String> items) {
        if (items == null || items.isEmpty()) return;

        List<SvcReqItem> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            rows.add(SvcReqItem.builder()
                    .svcReqSn(svcReqSn)
                    .svcReqItmCn(items.get(i))
                    .svcReqItmSortNo(i)
                    .build());
        }
        svcReqItemMapper.insertAll(rows);
    }

    // 업로드된 파일 id 목록을 SVC_REQ_IMAGE로 연결 — 순서를 정렬순서로 보존(0번이 대표이미지)
    private void saveImages(Long svcReqSn, Long usrSn, List<Long> flSnList) {
        if (flSnList == null || flSnList.isEmpty()) return;

        List<SvcReqImage> images = new ArrayList<>();
        for (int i = 0; i < flSnList.size(); i++) {
            Long flSn = flSnList.get(i);
            fileStorageService.requireOwnedServiceRequestFile(flSn, usrSn);
            images.add(SvcReqImage.builder()
                    .svcReqSn(svcReqSn)
                    .flSn(flSn)
                    .svcReqImgSortNo(i)
                    .build());
        }
        svcReqImageMapper.insertAll(images);
    }
}
