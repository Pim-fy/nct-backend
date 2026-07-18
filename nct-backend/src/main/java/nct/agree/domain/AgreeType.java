package nct.agree.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [동의 - 동의 유형 코드] (F-OPS-017)
 * - AGREE_HISTORY.AGR_TYPE_CD에 저장되는 공통코드(AGRG01) — "무엇에 동의했나"
 */
@Getter
@RequiredArgsConstructor
public enum AgreeType {

    /** 서비스 이용약관 */
    TERMS_OF_SERVICE("AGRC0001"),
    /** 개인정보 처리방침 */
    PRIVACY_POLICY("AGRC0002"),
    /** 마케팅 정보 수신 */
    MARKETING("AGRC0003");

    /** DB에 저장되는 공통코드 값 */
    private final String code;
}
