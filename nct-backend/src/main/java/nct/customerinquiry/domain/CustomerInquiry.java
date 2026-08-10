package nct.customerinquiry.domain;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/** 담당자 7 · 관리자 대상 1:1 고객 문의의 저장 상태를 전달하는 도메인 객체다. */
@Getter
@Builder
public class CustomerInquiry {

    private Long inquirySn;
    private Long userSn;
    private String typeCode;
    private String statusCode;
    private String title;
    private String content;
    private Long processorUserSn;
    private String answerContent;
    private LocalDateTime answeredAt;
    private String useYn;
    private LocalDateTime registeredAt;
    private LocalDateTime updatedAt;
    private String registeredBy;
    private String updatedBy;

    public void setInquirySn(Long inquirySn) {
        this.inquirySn = inquirySn;
    }
}
