package nct.point.domain;

import lombok.Data;

/**
 * Claude Code 작성 (BJN, 2026-07-21)
 *
 * [경매 정책 읽기 계약 - 모델] (담당자5 옥동민 요청, SYSTEM_SETTING 소비)
 * - SYSTEM_SETTING은 여러 도메인 설정을 담은 단일 행 테이블이지만(D-010), 경매 서비스가
 *   자동연장·최소입찰단위 판단에 쓰는 3개 컬럼만 담는다(도메인별 별도 조회 방침,
 *   [[SystemSetting]] 충전 한도 모델과 같은 패턴 — 관리자 전체 조회용 SystemSettingDetail과는 별개)
 */
@Data
public class AuctionPolicy {

    /** 경매 자동연장 기준 분 — 마감 N분 전 입찰 시 연장 (SYS_SET_AUC_EXT_MIN) */
    private Integer aucExtMin;

    /** 경매 자동연장 최대 횟수 (SYS_SET_AUC_EXT_MAX_CNT) */
    private Integer aucExtMaxCnt;

    /** 최소 입찰 단위 (원) (SYS_SET_MIN_BID_UNIT) */
    private Long minBidUnit;
}
