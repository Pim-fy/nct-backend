package nct.customerinquiry.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nct.auth.service.SuspendedInquiryTokenService;
import nct.customerinquiry.dto.CustomerInquiryCreateRequest;
import nct.customerinquiry.dto.CustomerInquiryCreateResponse;
import nct.customerinquiry.dto.SuspendedInquiryCreateRequest;
import nct.customerinquiry.service.CustomerInquiryService;
import nct.global.response.ApiResponse;

// @ai_generated
/**
 * F-AUTH-017/POL-AUTH-016: 정지 계정이 로그인 없이 문의를 접수하는 전용 경로다.
 * SecurityConfig에서 permitAll로 열려 있다 - 인가는 로그인 세션이 아니라
 * SuspendedInquiryTokenService가 검증하는 단발성 토큰이 대신한다.
 */
@RestController
@RequestMapping("/api/customer-inquiries/suspended")
@RequiredArgsConstructor
public class SuspendedInquiryController {

    // @ai_generated: 문의유형 공통코드(INQG01) 신규 값 - 사용자가 직접 CMM_CODE에 추가한다(코드 데이터,
    // 스키마 변경 아님). 클라이언트가 유형을 직접 지정하지 못하도록 서버에서 고정한다.
    private static final String SUSPENDED_INQUIRY_TYPE = "INQC0010";

    private final SuspendedInquiryTokenService suspendedInquiryTokenService;
    private final CustomerInquiryService customerInquiryService;

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerInquiryCreateResponse>> create(
            @Valid @RequestBody SuspendedInquiryCreateRequest request) {
        Long usrSn = suspendedInquiryTokenService.consumeToken(request.inquiryToken());
        CustomerInquiryCreateResponse response = customerInquiryService.create(
                usrSn,
                new CustomerInquiryCreateRequest(
                        SUSPENDED_INQUIRY_TYPE,
                        request.title(),
                        request.content(),
                        UUID.randomUUID().toString()));
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }
}
