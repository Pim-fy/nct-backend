package nct.ops.member.dto;

import java.util.List;
import java.util.Map;

import nct.abuse.dto.AdminAbuseReportResponse;
import nct.ops.member.port.AccountSanctionHistory;
import nct.member.dto.AdminMemberIdentityResponse;

/** 담당자 7 · F-OPS-002: 회원 기본정보와 최근 신고·제재 이력을 조립한 상세 응답입니다. */
public record AdminMemberDetailResponse(
        AdminMemberListItemResponse member,
        List<AdminAbuseReportResponse> reports,
        List<AccountSanctionHistory> sanctions,
        Map<Long, AdminMemberIdentityResponse> sanctionProcessorMembers,
        boolean sanctionHistoryAvailable,
        boolean restrictionAvailable) {
}
