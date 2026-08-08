package nct.customerinquiry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 담당자 7 · 고객 문의 처리 시작의 재시도 식별값을 받는다. */
public record AdminCustomerInquiryStartRequest(
        @NotBlank @Size(max = 36) String requestId) {
}
