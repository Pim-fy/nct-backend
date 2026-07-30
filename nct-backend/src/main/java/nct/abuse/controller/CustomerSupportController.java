package nct.abuse.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.abuse.dto.CustomerSupportReportRequest;
import nct.abuse.service.AbuseReportService;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customer-support")
public class CustomerSupportController {

    private final AbuseReportService abuseReportService;

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/reports")
    public ResponseEntity<ApiResponse<Void>> submitReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CustomerSupportReportRequest request) {

        Long reporterUserSn = userDetails.getMember().getId();
        abuseReportService.submitCustomerSupportReport(reporterUserSn, request);
        return ResponseEntity.status(201).body(ApiResponse.created(null));
    }
}
