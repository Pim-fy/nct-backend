package nct.customerinquiry.dto;

import java.time.LocalDateTime;

/** 담당자 7 · 관리자 문의 관리 목록의 한 행이다. */
public record AdminCustomerInquiryListItemResponse(
        Long inquirySn,
        Long userSn,
        String inquiryTypeCode,
        String inquiryTypeName,
        String statusCode,
        String statusName,
        String title,
        Long processorUserSn,
        LocalDateTime registeredAt,
        LocalDateTime updatedAt,
        LocalDateTime answeredAt) {
}
