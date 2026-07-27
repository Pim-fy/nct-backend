package nct.auth.domain;

import java.time.LocalDateTime;

// @ai_generated: 작업단위5 작업 2 - UserOauthMapper.findByUsrSn 조회 결과 매핑용.
/** USER_OAUTH 연동 목록 조회 결과 (마이페이지 연동 조회·최소 로그인 수단 검사 공용) */
public record UserOauthLinkRow(String providerCd, LocalDateTime regDt) {
}
