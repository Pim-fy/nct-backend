package nct.customerinquiry.dto;

import java.util.List;

/** 담당자 7 · 기존 관리자 목록 화면과 동일한 형식의 고객 문의 페이지 응답이다. */
public record AdminCustomerInquiryPageResponse(
        List<AdminCustomerInquiryListItemResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}
