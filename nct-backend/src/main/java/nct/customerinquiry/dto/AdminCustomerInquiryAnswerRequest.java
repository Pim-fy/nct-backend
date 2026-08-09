package nct.customerinquiry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 담당자 7 · 처리중인 고객 문의에 답변을 한 번 등록하는 요청이다. */
public record AdminCustomerInquiryAnswerRequest(
        @NotBlank @Size(max = 4000) String answer,
        @NotBlank @Size(max = 36) String detectionKey) {
}
