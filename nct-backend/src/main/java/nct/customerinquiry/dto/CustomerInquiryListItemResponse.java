package nct.customerinquiry.dto;

import java.time.LocalDateTime;

/** 담당자 7 · 마이페이지 1:1 문의 목록의 한 행이다. */
public record CustomerInquiryListItemResponse(
        Long inquirySn,
        String inquiryTypeCode,
        String inquiryTypeName,
        String statusCode,
        String statusName,
        String title,
        LocalDateTime registeredAt,
        LocalDateTime updatedAt,
        LocalDateTime answeredAt) {
}
