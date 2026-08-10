package nct.provider.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import nct.member.dto.AdminMemberIdentityResponse;

/** 담당자 6 · 신청자/관리자 화면이 함께 사용하는 제공자 카테고리별 심사 조회 결과입니다.
 *  (헤더의 "담당자 7" 표기는 업무분장 변경 전 잔재라 정정 — 2026-08-05)
 *  decidedAt 필드는 매퍼 resultMap에 매핑이 없어 항상 null로만 직렬화되던 죽은 필드라 제거(2026-08-05 점검 정리). */
@Getter @Setter
public class ProviderApplicationResponse {
    private Long applicationSn;
    private Long userSn;
    private String userName;
    private AdminMemberIdentityResponse applicantMember;
    private Long categorySn;
    private String categoryName;
    private String statusCode;
    private String statusName;
    private String rejectReason;
    private String applicationTypeCode;
    private LocalDateTime requestedAt;
    private Long processorUserSn;
    private AdminMemberIdentityResponse processorMember;
    private LocalDateTime processedAt;
    private String processedReason;
    private List<ProviderApplicationFileResponse> files = List.of();
}
