package nct.setting.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [시스템 설정 - 전체 설정 행 모델] (F-OPS-024)
 * - SYSTEM_SETTING 단일 행 전체. 경매·거래·견적·포인트·이메일·점검 모드의 고정 운영 설정
 * - 포인트 충전 검증용 최소 모델(nct.point.domain.SystemSetting — 충전 한도 2컬럼)과 별개다:
 *   그쪽은 충전 검증 전용이고, 이쪽은 관리자 조회·수정 화면 전용 (도메인별 별도 조회 방침)
 */
@Data
public class SystemSettingDetail {

    private Long sysSetSn;

    /** 경매 자동연장 기준 분 — 마감 N분 전 입찰 시 연장 */
    private Integer aucExtMin;
    /** 경매 자동연장 최대 횟수 */
    private Integer aucExtMaxCnt;
    /** 최소 입찰 단위 (원) */
    private Long minBidUnit;

    /** 거래 상대방 확인 기한 일수 — 지나면 자동완료 후보 */
    private Integer trdCfmnDays;
    /** 자동완료 처리 여부 Y/N */
    private String autoCmplYn;

    /** 최소/최대 충전금액 — 충전 화면·서버 검증이 실제로 읽는 값 */
    private Long minChrgAmt;
    private Long maxChrgAmt;

    /** 서비스 수수료율 (0~1, 예: 0.0300 = 3%) — MVP는 0원 거래 수수료(F-PAY-008)와 별개의 서비스 영역 설정 */
    private BigDecimal svcFeeRate;

    /** 견적 기본 유효기간 일수 */
    private Integer qutExpDays;

    /** 점검 모드 여부 Y/N + 기간 */
    private String mntncYn;
    private LocalDateTime mntncBgngDt;
    private LocalDateTime mntncEndDt;

    /** 이메일 발송 활성화 여부 Y/N */
    private String emailYn;

    private LocalDateTime sysSetUpdtDt;
}
