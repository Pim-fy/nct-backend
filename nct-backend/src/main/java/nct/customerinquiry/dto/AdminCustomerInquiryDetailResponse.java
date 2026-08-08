package nct.customerinquiry.dto;

import java.time.LocalDateTime;

/** 담당자 7 · ROLE_ADMIN 전용 고객 문의 상세와 답변 처리 결과다. */
public record AdminCustomerInquiryDetailResponse(
        Long inquirySn,
        Long userSn,
        String inquiryTypeCode,
        String inquiryTypeName,
        String statusCode,
        String statusName,
        String title,
        String content,
        Long processorUserSn,
        String answer,
        LocalDateTime registeredAt,
        LocalDateTime updatedAt,
        LocalDateTime answeredAt) {
}
