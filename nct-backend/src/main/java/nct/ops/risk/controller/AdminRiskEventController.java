package nct.ops.risk.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.ops.risk.dto.AdminRiskEventPageResponse;
import nct.ops.risk.dto.AdminRiskEventTypeSummaryResponse;
import nct.ops.risk.service.AdminRiskEventService;

/** 담당자 7 · F-OPS-011: ROLE_ADMIN 보호 규칙을 따르는 리스크 목록·집계 API입니다. */
@RestController
@RequestMapping("/api/admin/risk-events")
@RequiredArgsConstructor
public class AdminRiskEventController {

    private final AdminRiskEventService adminRiskEventService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminRiskEventPageResponse>> getRiskEvents(
            @RequestParam(name = "typeCode", required = false) String typeCode,
            @RequestParam(name = "processed", required = false) String processed,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                adminRiskEventService.getRiskEvents(typeCode, processed, page, size)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<List<AdminRiskEventTypeSummaryResponse>>> getTypeSummary(
            @RequestParam(name = "processed", required = false) String processed) {
        return ResponseEntity.ok(ApiResponse.success(adminRiskEventService.getTypeSummary(processed)));
    }
}
