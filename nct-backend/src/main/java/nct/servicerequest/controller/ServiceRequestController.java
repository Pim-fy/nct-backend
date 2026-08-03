package nct.servicerequest.controller;

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
import nct.servicerequest.dto.ServiceRequestResponse;
import nct.servicerequest.service.ServiceRequestService;

/**
 * [서비스 요청서 API] (F-SVC-001~004)
 *
 *  POST   /api/service-requests                요청서 등록          (authenticated)
 *  PUT    /api/service-requests/{svcReqSn}      임시저장 수정·공개 전환 (authenticated, 본인만)
 *  PATCH  /api/service-requests/{svcReqSn}/close 요청서 마감           (authenticated, 본인만)
 *  GET    /api/service-requests                 공개 요청서 검색      (permit-all, F-COM-002)
 *  GET    /api/service-requests/me              내 요청서 목록        (authenticated)
 *  GET    /api/service-requests/{svcReqSn}       요청서 상세 조회      (permit-all)
 *  DELETE /api/service-requests/{svcReqSn}       요청서 삭제           (authenticated, 본인만)
 */
@RestController
@RequestMapping("/api/service-requests")
@RequiredArgsConstructor
public class ServiceRequestController {

    private final ServiceRequestService serviceRequestService;

    /** 요청서 등록 */
    @PostMapping
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> registerServiceRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ServiceRequestRegisterRequest request) {

        Long usrSn = userDetails.getMember().getId();
        ServiceRequestResponse response = serviceRequestService.registerServiceRequest(usrSn, request);
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    /** 임시저장 요청서 수정 및 공개 전환 */
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
    @PatchMapping("/{svcReqSn}/close")
    public ResponseEntity<ApiResponse<Void>> closeServiceRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "svcReqSn") Long svcReqSn) {

        Long usrSn = userDetails.getMember().getId();
        serviceRequestService.closeServiceRequest(svcReqSn, usrSn);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 공개 요청서 검색 (F-COM-002 · 동민씨 서비스 탐색·홈 큐레이션용 Reader 계약) */
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

    /** 내 요청서 목록 — SecurityConfig에서 /api/service-requests/* 전체를 permit-all로 열었으므로 메서드 레벨에서 인증 강제 */
    @PreAuthorize("isAuthenticated()")
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

    /** 요청서 상세 조회 */
    @GetMapping("/{svcReqSn}")
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> getServiceRequest(
            @PathVariable(name = "svcReqSn") Long svcReqSn) {

        return ResponseEntity.ok(ApiResponse.success(serviceRequestService.getServiceRequest(svcReqSn)));
    }

    /** 요청서 삭제 (논리 삭제) */
    @DeleteMapping("/{svcReqSn}")
    public ResponseEntity<ApiResponse<Void>> deleteServiceRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "svcReqSn") Long svcReqSn) {

        Long usrSn = userDetails.getMember().getId();
        serviceRequestService.deleteServiceRequest(svcReqSn, usrSn);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
