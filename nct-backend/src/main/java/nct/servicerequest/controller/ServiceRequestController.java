package nct.servicerequest.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import nct.global.dto.PagedResponse;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;
import nct.servicerequest.dto.ServiceRequestRegisterRequest;
import nct.servicerequest.dto.ServiceRequestFormResponse;
import nct.servicerequest.dto.ServiceRequestQuoteSelectionResponse;
import nct.servicerequest.dto.ServiceRequestResponse;
import nct.servicerequest.dto.SvcReqCommentRequest;
import nct.servicerequest.dto.SvcReqCommentResponse;
import nct.servicerequest.service.ServiceRequestService;
import nct.servicerequest.service.ServiceRequestFormService;
import nct.servicerequest.service.ServiceRequestQuoteSelectionService;

/**
 * [서비스 요청서 API] (F-SVC-001~004)
 *
 *  POST   /api/service-requests                 일반회원 요청서 등록
 *  PUT    /api/service-requests/{svcReqSn}      일반회원 임시저장 수정·공개 전환 (본인만)
 *  PATCH  /api/service-requests/{svcReqSn}/close 일반회원 요청서 마감 (본인만)
 *  GET    /api/service-requests                 제공자 공개 요청서 검색
 *  GET    /api/service-requests/me              일반회원 내 요청서 목록
 *  GET    /api/service-requests/{svcReqSn}       요청자 본인 또는 제공자 상세 조회
 *  DELETE /api/service-requests/{svcReqSn}       일반회원 요청서 삭제 (본인만)
 *  POST   /api/service-requests/{svcReqSn}/comments  변경사항 추가 (본인만, 최대 3개)
 *  GET    /api/service-requests/{svcReqSn}/comments  변경사항 목록 조회
 */
@RestController
@RequestMapping("/api/service-requests")
@RequiredArgsConstructor
public class ServiceRequestController {

    private final ServiceRequestService serviceRequestService;
    private final ServiceRequestFormService serviceRequestFormService;
    private final ServiceRequestQuoteSelectionService serviceRequestQuoteSelectionService;

    /** F-SVC-002 현재 활성 카테고리별 동적 폼 정의 */
    @PreAuthorize("hasAuthority('ROLE_USER')")
    @GetMapping("/forms")
    public ResponseEntity<ApiResponse<List<ServiceRequestFormResponse>>> getActiveForms() {
        return ResponseEntity.ok(ApiResponse.success(serviceRequestFormService.getActiveForms()));
    }

    /** F-SVC-002 기존 임시저장 요청서가 작성 당시 폼 버전을 복원할 때 사용 */
    @PreAuthorize("hasAuthority('ROLE_USER')")
    @GetMapping("/forms/{formTemplateSn}")
    public ResponseEntity<ApiResponse<ServiceRequestFormResponse>> getFormByTemplateSn(
            @PathVariable(name = "formTemplateSn") Long formTemplateSn) {
        return ResponseEntity.ok(ApiResponse.success(
                serviceRequestFormService.getFormByTemplateSn(formTemplateSn)));
    }

    /** 요청서 등록 */
    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> registerServiceRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ServiceRequestRegisterRequest request) {

        Long usrSn = userDetails.getMember().getId();
        ServiceRequestResponse response = serviceRequestService.registerServiceRequest(usrSn, request);
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    /** 임시저장 요청서 수정 및 공개 전환 */
    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PutMapping("/{svcReqSn}")
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> updateServiceRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "svcReqSn") Long svcReqSn,
            @Valid @RequestBody ServiceRequestRegisterRequest request) {

        Long usrSn = userDetails.getMember().getId();
        ServiceRequestResponse response = serviceRequestService.updateServiceRequest(svcReqSn, usrSn, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 요청서 마감 (F-SVC-003) */
    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PatchMapping("/{svcReqSn}/close")
    public ResponseEntity<ApiResponse<Void>> closeServiceRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "svcReqSn") Long svcReqSn) {

        Long usrSn = userDetails.getMember().getId();
        serviceRequestService.closeServiceRequest(svcReqSn, usrSn);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 마감된 요청서 재등록 — 내용을 복사한 새 임시저장 요청서를 만든다 (원본은 이력으로 유지) */
    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping("/{svcReqSn}/reregister")
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> reregisterServiceRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "svcReqSn") Long svcReqSn) {

        Long usrSn = userDetails.getMember().getId();
        ServiceRequestResponse response = serviceRequestService.reregisterServiceRequest(svcReqSn, usrSn);
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    /** 담당자 7 통합, F-SVC-010/013: 견적 선택부터 보관금·매칭 완료까지 한 번에 처리한다. */
    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping("/{svcReqSn}/quotes/{quoteId}/select")
    public ResponseEntity<ApiResponse<ServiceRequestQuoteSelectionResponse>> selectQuoteAndCreateTrade(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "svcReqSn") Long svcReqSn,
            @PathVariable(name = "quoteId") Long quoteId) {

        Long requesterUsrSn = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                serviceRequestQuoteSelectionService.selectQuoteAndCreateTrade(
                        svcReqSn,
                        quoteId,
                        requesterUsrSn)));
    }

    /** 담당자 7 통합: 제공자 모드에서 일반회원의 공개 요청서를 검색한다. */
    @PreAuthorize("hasAuthority('ROLE_SERVICE')")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ServiceRequestResponse>>> searchServiceRequests(
            @RequestParam(name = "keyword", required = false)     String keyword,
            @RequestParam(name = "categorySn", required = false)  Long   categorySn,
            @RequestParam(name = "minBudget", required = false)   Long   minBudget,
            @RequestParam(name = "maxBudget", required = false)   Long   maxBudget,
            @RequestParam(name = "sort", defaultValue = "latest") String sort,
            @RequestParam(name = "page", defaultValue = "1")      int    page,
            @RequestParam(name = "size", defaultValue = "10")     int    size) {

        PagedResponse<ServiceRequestResponse> response =
                serviceRequestService.searchServiceRequests(keyword, categorySn, minBudget, maxBudget, sort, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 일반회원의 내 요청서 목록 */
    @PreAuthorize("hasAuthority('ROLE_USER')")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PagedResponse<ServiceRequestResponse>>> getMyServiceRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(name = "page", defaultValue = "1")  int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "filterType", required = false) String filterType) {

        Long usrSn = userDetails.getMember().getId();
        PagedResponse<ServiceRequestResponse> response = serviceRequestService.getMyServiceRequests(usrSn, page, size, filterType);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 일반회원은 본인 요청만, 제공자는 공개 요청을 조회한다. */
    @PreAuthorize("hasAnyAuthority('ROLE_USER', 'ROLE_SERVICE')")
    @GetMapping("/{svcReqSn}")
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> getServiceRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "svcReqSn") Long svcReqSn) {

        Long viewerUsrSn = userDetails.getMember().getId();
        boolean providerViewer = userDetails.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SERVICE".equals(authority.getAuthority()));
        return ResponseEntity.ok(ApiResponse.success(
                serviceRequestService.getServiceRequest(svcReqSn, viewerUsrSn, providerViewer)));
    }

    /** 임시저장 수정 화면 전용 상세 — 소유자에게만 구조화 답변과 복호화 주소 반환 */
    @PreAuthorize("hasAuthority('ROLE_USER')")
    @GetMapping("/{svcReqSn}/edit")
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> getEditableServiceRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "svcReqSn") Long svcReqSn) {
        Long usrSn = userDetails.getMember().getId();
        return ResponseEntity.ok(ApiResponse.success(
                serviceRequestService.getEditableServiceRequest(svcReqSn, usrSn)));
    }

    /** 요청서 삭제 (논리 삭제) */
    @PreAuthorize("hasAuthority('ROLE_USER')")
    @DeleteMapping("/{svcReqSn}")
    public ResponseEntity<ApiResponse<Void>> deleteServiceRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "svcReqSn") Long svcReqSn) {

        Long usrSn = userDetails.getMember().getId();
        serviceRequestService.deleteServiceRequest(svcReqSn, usrSn);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 요청서 변경사항 추가 (본인만, 견적 요청 정책상 최대 3개) */
    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping("/{svcReqSn}/comments")
    public ResponseEntity<ApiResponse<SvcReqCommentResponse>> addComment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "svcReqSn") Long svcReqSn,
            @Valid @RequestBody SvcReqCommentRequest request) {

        Long usrSn = userDetails.getMember().getId();
        SvcReqCommentResponse response = serviceRequestService.addComment(svcReqSn, usrSn, request);
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    /** 요청서 변경사항 목록 조회 — 요청자 본인 또는 제공자 */
    @PreAuthorize("hasAnyAuthority('ROLE_USER', 'ROLE_SERVICE')")
    @GetMapping("/{svcReqSn}/comments")
    public ResponseEntity<ApiResponse<List<SvcReqCommentResponse>>> getComments(
            @PathVariable(name = "svcReqSn") Long svcReqSn) {

        return ResponseEntity.ok(ApiResponse.success(serviceRequestService.getComments(svcReqSn)));
    }
}
