package nct.agree.domain;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [동의 - 동의 이력 행 모델] (F-OPS-017)
 * - AGREE_HISTORY 한 행. "누가, 어떤 행위 시점에, 무엇에, 동의했나/안 했나"의 증적 단위
 * - 참조 일련번호 5개(입찰·경매·포인트원장·거래문제·거래)는 DB CHECK 제약으로
 *   "정확히 1개만" 채워져야 한다 — 서비스 계약(record)에서도 같은 검증을 한다
 */
@Data
public class AgreeHistory {

    private Long agrHstSn;
    private Long usrSn;

    // ---------- 다형성 참조 (정확히 1개만 non-null) ----------
    private Long bidSn;
    private Long aucSn;
    private Long ptLdgSn;
    private Long trdDspSn;
    private Long trdSn;

    /** 동의유형공통코드(AGRG01) — 무엇에 동의했나 */
    private String agrTypeCd;
    /** 동의행위유형공통코드(AGRG02) — 어떤 행위 시점이었나 */
    private String agrActTypeCd;
    /** 동의여부 Y/N — 거부 이력도 증적으로 남긴다 */
    private String agrHstAgrYn;

    private LocalDateTime agrHstRegDt;
}
