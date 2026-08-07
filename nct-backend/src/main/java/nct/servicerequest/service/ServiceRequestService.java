package nct.servicerequest.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;

import lombok.RequiredArgsConstructor;
import nct.file.service.FileStorageService;
import nct.global.dto.PagedResponse;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.servicerequest.domain.ServiceRequest;
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
import nct.servicerequest.port.ServiceRequestQuoteReader;
import nct.servicerequest.port.ServiceRequestQuoteReader.ServiceRequestQuoteTarget;
import nct.servicerequest.service.ServiceRequestFormService.ValidatedSubmission;

@Service
@RequiredArgsConstructor
public class ServiceRequestService implements ServiceRequestQuoteReader, AdminServiceRequestReader {

    private final ServiceRequestMapper serviceRequestMapper;
    private final SvcReqItemMapper svcReqItemMapper;
    private final SvcReqImageMapper svcReqImageMapper;
    private final SvcReqCommentMapper svcReqCommentMapper;
    private final ServiceRequestFormService serviceRequestFormService;
    private final FileStorageService fileStorageService;

    // 변경사항 추가는 공개·매칭완료 상태(요청서가 이미 노출된 상태)에서만, 최대 3개까지
    private static final Set<String> COMMENTABLE_STATUS_CD = Set.of("SVCC0002", "SVCC0003");
    private static final int MAX_COMMENT_COUNT = 3;

    // 클라이언트가 직접 지정할 수 있는 요청서 상태 — 그 외 내부 전이 상태는 서버만 부여
    private static final Set<String> CLIENT_ALLOWED_STATUS_CD = Set.of("SVCC0001", "SVCC0002");
    private static final Set<String> SERVICE_REQUEST_STATUS_CD =
            Set.of("SVCC0001", "SVCC0002", "SVCC0003", "SVCC0004");

    private void validateClientStatusCd(String statusCd) {
        if (!CLIENT_ALLOWED_STATUS_CD.contains(statusCd)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "허용되지 않는 요청서 상태값입니다.");
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

    @Transactional
    public ServiceRequestResponse registerServiceRequest(Long usrSn, ServiceRequestRegisterRequest req) {
        String statusCd = (req.getSvcReqStatusCd() != null) ? req.getSvcReqStatusCd() : "SVCC0002";
        validateClientStatusCd(statusCd);
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
            return buildOwnerResponse(existing);
        }

        // 담당자 7 통합: 일반회원은 URL을 직접 입력해도 다른 회원의 요청을 볼 수 없다.
        if (!providerViewer) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        // 담당자 7: 정본 F-SVC-003에 따라 임시저장은 존재 여부까지 외부에 노출하지 않는다.
        ServiceRequestResponse response = serviceRequestMapper.findPublicServiceRequestById(svcReqSn)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));
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
        if (!Character.valueOf('Y').equals(existing.getSvcReqUseYn())) {
            throw new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND);
        }

        boolean owner = existing.getUsrSn().equals(viewerUsrSn);
        if (providerViewer) {
            serviceRequestMapper.findPublicServiceRequestById(svcReqSn)
                    .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_REQUEST_NOT_FOUND));
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

    @Transactional(readOnly = true)
    public PagedResponse<ServiceRequestResponse> getMyServiceRequests(Long usrSn, int page, int size, String filterType) {
        PageHelper.startPage(page, size);
        List<ServiceRequestResponse> list = serviceRequestMapper.findMyServiceRequests(usrSn, filterType);
        return PagedResponse.of(new PageInfo<>(list));
    }

    /** 요청서 마감 — 공개(SVCC0002) 상태에서만 종료(SVCC0004)로 전환 가능 (F-SVC-003) */
    @Transactional
    public void closeServiceRequest(Long svcReqSn, Long usrSn) {
        ServiceRequest serviceRequest = serviceRequestMapper.findServiceRequestEntityById(svcReqSn)
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
    }

    /** 견적 요청 기간 만료 대상 조회 (F-SVC-003, 배치 전용) */
    @Transactional(readOnly = true)
    public List<Long> findExpiredOpenServiceRequestIds(int limit) {
        return serviceRequestMapper.findExpiredOpenServiceRequestIds(limit);
    }

    /** 견적 요청 기간 만료 자동 마감 — 이미 처리됐거나 상태가 바뀐 건은 조용히 넘어간다 (F-SVC-003, 배치 전용) */
    @Transactional
    public void autoCloseExpiredServiceRequest(Long svcReqSn) {
        serviceRequestMapper.autoCloseServiceRequest(svcReqSn);
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
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "공개 또는 매칭완료 상태의 요청서에만 변경사항을 추가할 수 있습니다.");
        }
        if (svcReqCommentMapper.findLatestComments(svcReqSn, Integer.MAX_VALUE).size() >= MAX_COMMENT_COUNT) {
            throw new CustomException(ErrorCode.CONFLICT, "변경사항은 최대 " + MAX_COMMENT_COUNT + "개까지 등록할 수 있습니다.");
        }

        SvcReqComment comment = SvcReqComment.builder()
                .svcReqSn(svcReqSn)
                .usrSn(usrSn)
                .svcReqCmtTtl(req.getTtl())
                .svcReqCmtCn(req.getCn())
                .svcReqCmtRegId(String.valueOf(usrSn))
                .build();
        svcReqCommentMapper.insertComment(comment);

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
