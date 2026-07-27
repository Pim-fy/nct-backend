package nct.point.domain;

import lombok.Data;

/**
 * Claude Code 작성 (BJN, 2026-07-16)
 *
 * [포인트 - 시스템 설정(충전 한도) 모델]
 * - SYSTEM_SETTING은 여러 도메인 설정을 담은 단일 행 테이블이지만(D-010),
 *   담당자6은 충전 검증에 필요한 최소·최대 충전금액 두 컬럼만 쓴다
 *   (다른 컬럼이 필요한 도메인이 생기면 그쪽에서 별도로 조회하면 된다 — 여기서 전체를 대표하지 않음)
 */
@Data
public class SystemSetting {

    /** 최소 충전금액 (SYS_SET_MIN_CHRG_AMT) */
    private long minChrgAmt;

    /** 최대 충전금액 (SYS_SET_MAX_CHRG_AMT) */
    private long maxChrgAmt;
}
