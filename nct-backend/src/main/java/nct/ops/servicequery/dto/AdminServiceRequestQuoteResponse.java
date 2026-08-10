package nct.ops.servicequery.dto;

import java.time.LocalDateTime;

import nct.member.dto.AdminMemberIdentityResponse;
import nct.quote.dto.AdminQuoteListItem;

/** 담당자 7 · F-OPS-021: 관리자 서비스 요청 상세에 표시할 제출 견적 행입니다. */
public record AdminServiceRequestQuoteResponse(
        Long quoteId,
        Long providerUserId,
        AdminMemberIdentityResponse providerMember,
        Long amount,
        String statusCode,
        int reviseCount,
        LocalDateTime submittedAt,
        LocalDateTime updatedAt,
        boolean selected) {

    public static AdminServiceRequestQuoteResponse from(
            AdminQuoteListItem source,
            AdminMemberIdentityResponse providerMember,
            boolean selected) {
        return new AdminServiceRequestQuoteResponse(
                source.getQuoteId(),
                source.getProviderUserId(),
                providerMember,
                source.getAmount(),
                source.getStatusCode(),
                source.getReviseCount(),
                source.getSubmittedAt(),
                source.getUpdatedAt(),
                selected);
    }
}
