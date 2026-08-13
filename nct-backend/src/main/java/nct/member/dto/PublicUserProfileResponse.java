package nct.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 담당자 7 통합 연결 · F-COM-008~009 지원: 거래 프로필 헤더의 공개 허용 필드만 반환한다. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicUserProfileResponse {
    private Long userSn;
    private String displayName;
    private String profileImageUrl;
}
