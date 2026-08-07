package nct.ops.operation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.ops.operation.dto.AdminDisputeDetailResponse;
import nct.ops.operation.dto.AdminDisputeListRequest;
import nct.ops.operation.dto.AdminDisputePageResponse;
import nct.ops.operation.service.AdminDisputeQueryService;

/** 담당자 7 · F-OPS-005/REQ-OPS-005: ROLE_ADMIN 전용 거래 분쟁 목록·상세 조회 API입니다. */
@RestController
@RequestMapping("/api/admin/disputes")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
public class AdminDisputeQueryController {

    private final AdminDisputeQueryService service;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminDisputePageResponse>> getPage(
            @ModelAttribute AdminDisputeListRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.getPage(request)));
    }

    @GetMapping("/{disputeSn}")
    public ResponseEntity<ApiResponse<AdminDisputeDetailResponse>> getDetail(
            @PathVariable(name = "disputeSn") Long disputeSn) {
        return ResponseEntity.ok(ApiResponse.success(service.getDetail(disputeSn)));
    }
}
