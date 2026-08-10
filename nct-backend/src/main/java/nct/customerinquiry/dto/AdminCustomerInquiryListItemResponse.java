package nct.customerinquiry.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nct.member.dto.AdminMemberIdentityResponse;

/** 담당자 7 · 관리자 문의 관리 목록 응답입니다. 개인정보 원문은 포함하지 않습니다. */
@Getter
@Setter
@NoArgsConstructor
public class AdminCustomerInquiryListItemResponse {

    private Long inquirySn;
    private Long userSn;
    private String inquiryTypeCode;
    private String inquiryTypeName;
    private String statusCode;
    private String statusName;
    private String title;
    private Long processorUserSn;
    private LocalDateTime registeredAt;
    private LocalDateTime updatedAt;
    private LocalDateTime answeredAt;
    private AdminMemberIdentityResponse writerMember;
    private AdminMemberIdentityResponse processorMember;

    public AdminCustomerInquiryListItemResponse(
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
        this.inquirySn = inquirySn;
        this.userSn = userSn;
        this.inquiryTypeCode = inquiryTypeCode;
        this.inquiryTypeName = inquiryTypeName;
        this.statusCode = statusCode;
        this.statusName = statusName;
        this.title = title;
        this.processorUserSn = processorUserSn;
        this.registeredAt = registeredAt;
        this.updatedAt = updatedAt;
        this.answeredAt = answeredAt;
    }
}
