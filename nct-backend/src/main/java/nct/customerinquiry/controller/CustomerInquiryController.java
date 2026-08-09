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
import nct.customerinquiry.dto.CustomerInquiryCreateRequest;
import nct.customerinquiry.dto.CustomerInquiryCreateResponse;
import nct.customerinquiry.dto.CustomerInquiryDetailResponse;
import nct.customerinquiry.dto.CustomerInquiryListItemResponse;
import nct.customerinquiry.service.CustomerInquiryService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.ApiResponse;
import nct.global.response.PageResponse;
import nct.global.security.domain.CustomUserDetails;

/** 담당자 7 · 일반 사용자와 제공자 모드가 사용하는 관리자 대상 1:1 문의 API다. */
@RestController
@RequestMapping("/api/customer-inquiries")
@PreAuthorize("hasAnyAuthority('ROLE_USER', 'ROLE_SERVICE')")
@RequiredArgsConstructor
public class CustomerInquiryController {

    private final CustomerInquiryService customerInquiryService;

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerInquiryCreateResponse>> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CustomerInquiryCreateRequest request) {
        CustomerInquiryCreateResponse response =
                customerInquiryService.create(userSn(userDetails), request);
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<CustomerInquiryListItemResponse>>> getMyInquiries(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(name = "statusCode", required = false) String statusCode,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                customerInquiryService.getMyInquiries(
                        userSn(userDetails), statusCode, page, size)));
    }

    @GetMapping("/me/{inquirySn}")
    public ResponseEntity<ApiResponse<CustomerInquiryDetailResponse>> getMyInquiry(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "inquirySn") Long inquirySn) {
        return ResponseEntity.ok(ApiResponse.success(
                customerInquiryService.getMyInquiry(userSn(userDetails), inquirySn)));
    }

    private Long userSn(CustomUserDetails userDetails) {
        if (userDetails == null
                || userDetails.getMember() == null
                || userDetails.getMember().getId() == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userDetails.getMember().getId();
    }
}
