package nct.customerinquiry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// @ai_generated
/** F-AUTH-017: 정지 계정이 비로그인 상태로 문의를 접수할 때 사용하는 요청이다. */
public record SuspendedInquiryCreateRequest(
        @NotBlank String inquiryToken,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 4000) String content) {
}
