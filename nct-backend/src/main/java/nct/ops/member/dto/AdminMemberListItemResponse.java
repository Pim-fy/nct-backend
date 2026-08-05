package nct.ops.member.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import nct.member.dto.AdminMemberSource;

/** 담당자 7 · F-OPS-002: 민감정보를 제외한 관리자 회원 목록 응답입니다. */
@Builder
public record AdminMemberListItemResponse(
        Long userSn,
        String loginId,
        String nickname,
        String statusCode,
        String statusName,
        String roleCode,
        String roleName,
        Character useYn,
        long reportCount,
        LocalDateTime registeredAt,
        LocalDateTime updatedAt) {

    public static AdminMemberListItemResponse from(AdminMemberSource source) {
        return AdminMemberListItemResponse.builder()
                .userSn(source.getUserSn())
                .loginId(source.getLoginId())
                .nickname(source.getNickname())
                .statusCode(source.getStatusCode())
                .statusName(source.getStatusName())
                .roleCode(source.getRoleCode())
                .roleName(source.getRoleName())
                .useYn(source.getUseYn())
                .reportCount(source.getReportCount())
                .registeredAt(source.getRegisteredAt())
                .updatedAt(source.getUpdatedAt())
                .build();
    }
}
