package nct.customerinquiry.dto;

import java.time.LocalDateTime;

/** 담당자 7 · 작성자 본인에게만 반환하는 1:1 문의 상세다. */
public record CustomerInquiryDetailResponse(
        Long inquirySn,
        String inquiryTypeCode,
        String inquiryTypeName,
        String statusCode,
        String statusName,
        String title,
        String content,
        String answer,
        LocalDateTime registeredAt,
        LocalDateTime updatedAt,
        LocalDateTime answeredAt) {
}
