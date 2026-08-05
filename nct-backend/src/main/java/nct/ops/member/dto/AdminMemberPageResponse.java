package nct.ops.member.dto;

import java.util.List;

/** 담당자 7 · F-OPS-002: 관리자 회원 목록 페이징 결과입니다. */
public record AdminMemberPageResponse(
        List<AdminMemberListItemResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean restrictionAvailable) {
}
