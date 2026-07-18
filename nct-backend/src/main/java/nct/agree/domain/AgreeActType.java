package nct.agree.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [동의 - 동의 행위 유형 코드] (F-OPS-017)
 * - AGREE_HISTORY.AGR_ACT_TYPE_CD에 저장되는 공통코드(AGRG02) — "어떤 행위 시점의 동의였나"
 */
@Getter
@RequiredArgsConstructor
public enum AgreeActType {

    /** 회원가입 */
    SIGN_UP("AGRC0004"),
    /** 입찰 (즉시구매 포함 — 입찰 단계 동의) */
    BID("AGRC0005"),
    /** 포인트 홀딩 (보관금 전환 포함) */
    POINT_HOLD("AGRC0006"),
    /** 거래 완료 확인 */
    TRADE_COMPLETE_CONFIRM("AGRC0007"),
    /** 거래 문제(환불/분쟁) 접수 */
    TRADE_DISPUTE_SUBMIT("AGRC0008");

    /** DB에 저장되는 공통코드 값 */
    private final String code;
}
