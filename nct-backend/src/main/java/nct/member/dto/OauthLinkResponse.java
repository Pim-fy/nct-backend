package nct.member.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

// @ai_generated: 작업단위5 작업 2 (F-AUTH-016)
/** 마이페이지 연동 목록 조회 응답 - provider는 담당자3 프론트 친화적 키("kakao" 등) */
@Getter
@Builder
public class OauthLinkResponse {

    private String provider;
    private LocalDateTime linkedAt;
}
