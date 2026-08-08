package nct.customerinquiry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 담당자 7 · 고객센터에서 관리자에게 1:1 문의를 등록할 때 사용하는 요청이다. */
public record CustomerInquiryCreateRequest(
        @NotBlank @Size(max = 30) String inquiryTypeCode,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 4000) String content,
        @NotBlank @Size(max = 36) String detectionKey) {
}
