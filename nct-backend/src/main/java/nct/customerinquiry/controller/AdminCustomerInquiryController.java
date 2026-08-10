package nct.customerinquiry.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.customerinquiry.dto.AdminCustomerInquiryAnswerRequest;
import nct.customerinquiry.dto.AdminCustomerInquiryDetailResponse;
import nct.customerinquiry.dto.AdminCustomerInquiryPageResponse;
import nct.customerinquiry.dto.AdminCustomerInquiryStartRequest;
import nct.customerinquiry.service.CustomerInquiryService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.ApiResponse;
import nct.global.security.domain.CustomUserDetails;

/** 담당자 7 · ROLE_ADMIN만 조회·처리·답변할 수 있는 고객 문의 관리 API다. */
@RestController
@RequestMapping("/api/admin/customer-inquiries")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
public class AdminCustomerInquiryController {

    private final CustomerInquiryService customerInquiryService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminCustomerInquiryPageResponse>> getInquiries(
            @RequestParam(name = "statusCode", required = false) String statusCode,
            @RequestParam(name = "inquiryTypeCode", required = false) String inquiryTypeCode,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                customerInquiryService.getAdminInquiries(
                        statusCode, inquiryTypeCode, keyword, page, size)));
    }

    @GetMapping("/{inquirySn}")
    public ResponseEntity<ApiResponse<AdminCustomerInquiryDetailResponse>> getInquiry(
            @PathVariable(name = "inquirySn") Long inquirySn) {
        return ResponseEntity.ok(ApiResponse.success(
                customerInquiryService.getAdminInquiry(inquirySn)));
    }

    @PostMapping("/{inquirySn}/start-processing")
    public ResponseEntity<ApiResponse<Void>> startProcessing(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "inquirySn") Long inquirySn,
            @Valid @RequestBody AdminCustomerInquiryStartRequest request) {
        customerInquiryService.startProcessing(
                inquirySn, adminUserSn(userDetails), request.requestId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/{inquirySn}/answer")
    public ResponseEntity<ApiResponse<Void>> answer(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "inquirySn") Long inquirySn,
            @Valid @RequestBody AdminCustomerInquiryAnswerRequest request) {
        customerInquiryService.answer(inquirySn, adminUserSn(userDetails), request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    private Long adminUserSn(CustomUserDetails userDetails) {
        if (userDetails == null
                || userDetails.getMember() == null
                || userDetails.getMember().getId() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userDetails.getMember().getId();
    }
}
