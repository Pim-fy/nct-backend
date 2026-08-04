package nct.ops.servicequery.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.ops.servicequery.dto.AdminServiceRequestListRequest;
import nct.ops.servicequery.service.AdminServiceRequestQueryService;
import nct.servicerequest.dto.AdminServiceRequestDetail;
import nct.servicerequest.dto.AdminServiceRequestPage;

/** 담당자 7 · 관리자 42: ROLE_ADMIN 전용 서비스 요청 목록·상세 조회 API다. */
@RestController
@RequestMapping("/api/admin/service-requests")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
public class AdminServiceRequestQueryController {
    private final AdminServiceRequestQueryService service;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminServiceRequestPage>> getPage(
            @ModelAttribute AdminServiceRequestListRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.getPage(request)));
    }

    @GetMapping("/{serviceRequestId}")
    public ResponseEntity<ApiResponse<AdminServiceRequestDetail>> getDetail(
            @PathVariable(name = "serviceRequestId") Long serviceRequestId) {
        return ResponseEntity.ok(ApiResponse.success(service.getDetail(serviceRequestId)));
    }
}
