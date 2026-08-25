package nct.ops.funds.controller;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.global.response.ApiResponse;
import nct.ops.funds.dto.AdminFundDashboardSummaryResponse;
import nct.ops.funds.service.AdminFundDashboardService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/funds")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFundDashboardController {

    private final AdminFundDashboardService adminFundDashboardService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminFundDashboardSummaryResponse>> getSummary(
            @RequestParam(name = "from")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(adminFundDashboardService.getSummary(from, to)));
    }
}
