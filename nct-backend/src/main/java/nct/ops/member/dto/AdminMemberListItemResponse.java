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

    private static final String SOCIAL_LOGIN_ID_PREFIX = "OAUTH_";

    public static AdminMemberListItemResponse from(AdminMemberSource source) {
        return AdminMemberListItemResponse.builder()
                .userSn(source.getUserSn())
                .loginId(safeLoginId(source.getLoginId()))
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

    /** 담당자 7 · POL-AUTH-010: 소셜 인증용 시스템 ID는 회원관리 응답에서도 숨깁니다. */
    private static String safeLoginId(String loginId) {
        return loginId != null && loginId.startsWith(SOCIAL_LOGIN_ID_PREFIX) ? null : loginId;
    }
}
