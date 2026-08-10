package nct.customerinquiry.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nct.member.dto.AdminMemberIdentityResponse;

/** 담당자 7 · ROLE_ADMIN 전용 고객 문의 상세 응답입니다. 개인정보 원문은 포함하지 않습니다. */
@Getter
@Setter
@NoArgsConstructor
public class AdminCustomerInquiryDetailResponse {

    private Long inquirySn;
    private Long userSn;
    private String inquiryTypeCode;
    private String inquiryTypeName;
    private String statusCode;
    private String statusName;
    private String title;
    private String content;
    private Long processorUserSn;
    private String answer;
    private LocalDateTime registeredAt;
    private LocalDateTime updatedAt;
    private LocalDateTime answeredAt;
    private AdminMemberIdentityResponse writerMember;
    private AdminMemberIdentityResponse processorMember;

    public AdminCustomerInquiryDetailResponse(
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
        this.inquirySn = inquirySn;
        this.userSn = userSn;
        this.inquiryTypeCode = inquiryTypeCode;
        this.inquiryTypeName = inquiryTypeName;
        this.statusCode = statusCode;
        this.statusName = statusName;
        this.title = title;
        this.content = content;
        this.processorUserSn = processorUserSn;
        this.answer = answer;
        this.registeredAt = registeredAt;
        this.updatedAt = updatedAt;
        this.answeredAt = answeredAt;
    }
}
