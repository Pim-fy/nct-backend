package nct.setting.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.audit.domain.AuditLogType;
import nct.audit.service.AuditLogService;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.setting.domain.SystemSettingDetail;
import nct.setting.mapper.SystemSettingAdminMapper;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [시스템 설정 - 관리자 서비스] (담당자6 백종남, F-OPS-024)
 *
 * SYSTEM_SETTING 단일 행을 앱 계층에서 조회·수정한다.
 * - 허용 범위 밖 값은 차단한다 (정본 예외 규칙)
 * - 수정은 관리자 감사로그(F-OPS-015)를 반드시 남긴다
 * - 카테고리 승인방식은 seed 고정 기준이라 이 화면에서 다루지 않는다
 * - 단일 행 강제(INSERT 차단)는 DB 제약이 아닌 앱 계층 방침: 이 서비스는 UPDATE만 하고
 *   INSERT/DELETE 경로 자체를 만들지 않는다
 */
@Service
@RequiredArgsConstructor
public class SystemSettingAdminService {

    private final SystemSettingAdminMapper settingMapper;
    private final AuditLogService auditLogService;

    /** 전체 설정 조회 (관리자 화면) */
    @Transactional(readOnly = true)
    public SystemSettingDetail get() {
        return settingMapper.selectOne();
    }

    /**
     * 전체 설정 수정 — 행 잠금 → 범위 검증 → UPDATE → 감사로그, 한 트랜잭션.
     * 어떤 값이 어떻게 바뀌었는지는 감사로그 사유에 요약해 남긴다.
     */
    @Transactional
    public SystemSettingDetail update(SystemSettingDetail request, long adminUsrSn, String ipAddr) {
        SystemSettingDetail current = settingMapper.selectOneForUpdate();
        validate(request);

        request.setSysSetSn(current.getSysSetSn());
        settingMapper.update(request);

        auditLogService.record(adminUsrSn, AuditLogType.UPDATE, RefType.SYSTEM_SETTING,
                current.getSysSetSn(), summarizeChanges(current, request), ipAddr);
        return settingMapper.selectOne();
    }

    /** 허용 범위 검증 — 하나라도 어긋나면 아무것도 저장하지 않는다 */
    private void validate(SystemSettingDetail s) {
        requirePositive(s.getAucExtMin(), "경매 자동연장 기준 분");
        requireNonNegative(s.getAucExtMaxCnt(), "경매 자동연장 최대 횟수");
        requirePositive(s.getMinBidUnit(), "최소 입찰 단위");
        requirePositive(s.getTrdCfmnDays(), "거래 확인 기한 일수");
        requirePositive(s.getQutExpDays(), "견적 유효기간 일수");
        requireYn(s.getAutoCmplYn(), "자동완료 처리 여부");
        requireYn(s.getMntncYn(), "점검 모드 여부");
        requireYn(s.getEmailYn(), "이메일 발송 여부");

        requirePositive(s.getMinChrgAmt(), "최소 충전금액");
        requirePositive(s.getMaxChrgAmt(), "최대 충전금액");
        if (s.getMinChrgAmt() > s.getMaxChrgAmt()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "최소 충전금액이 최대 충전금액보다 클 수 없습니다.");
        }

        // 수수료율은 0~100% 사이만 허용 (DB DECIMAL(5,4) 자릿수와도 일치)
        if (s.getSvcFeeRate() == null
                || s.getSvcFeeRate().compareTo(BigDecimal.ZERO) < 0
                || s.getSvcFeeRate().compareTo(BigDecimal.ONE) > 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "서비스 수수료율은 0과 1 사이여야 합니다 (예: 0.03 = 3%).");
        }

        // 점검 모드를 켜면 기간이 필수이고 시작이 종료보다 앞서야 한다
        if ("Y".equals(s.getMntncYn())) {
            if (s.getMntncBgngDt() == null || s.getMntncEndDt() == null) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                        "점검 모드를 켜려면 점검 시작·종료 일시를 입력해야 합니다.");
            }
            if (!s.getMntncBgngDt().isBefore(s.getMntncEndDt())) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                        "점검 시작 일시는 종료 일시보다 앞서야 합니다.");
            }
        }
    }

    private void requirePositive(Number value, String label) {
        if (value == null || value.longValue() <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, label + "은(는) 0보다 커야 합니다.");
        }
    }

    private void requireNonNegative(Number value, String label) {
        if (value == null || value.longValue() < 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, label + "은(는) 0 이상이어야 합니다.");
        }
    }

    private void requireYn(String value, String label) {
        if (!"Y".equals(value) && !"N".equals(value)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, label + "은(는) Y 또는 N이어야 합니다.");
        }
    }

    /** 감사로그 사유용 변경 요약 — "무엇이 무엇에서 무엇으로" 만 담는다 (변경 없으면 '변경 값 없음') */
    private String summarizeChanges(SystemSettingDetail before, SystemSettingDetail after) {
        StringBuilder sb = new StringBuilder("시스템 설정 수정:");
        appendIfChanged(sb, "자동연장기준분", before.getAucExtMin(), after.getAucExtMin());
        appendIfChanged(sb, "자동연장최대횟수", before.getAucExtMaxCnt(), after.getAucExtMaxCnt());
        appendIfChanged(sb, "최소입찰단위", before.getMinBidUnit(), after.getMinBidUnit());
        appendIfChanged(sb, "거래확인기한일수", before.getTrdCfmnDays(), after.getTrdCfmnDays());
        appendIfChanged(sb, "자동완료여부", before.getAutoCmplYn(), after.getAutoCmplYn());
        appendIfChanged(sb, "최소충전금액", before.getMinChrgAmt(), after.getMinChrgAmt());
        appendIfChanged(sb, "최대충전금액", before.getMaxChrgAmt(), after.getMaxChrgAmt());
        appendIfChanged(sb, "수수료율", before.getSvcFeeRate(), after.getSvcFeeRate());
        appendIfChanged(sb, "견적유효기간일수", before.getQutExpDays(), after.getQutExpDays());
        appendIfChanged(sb, "점검모드", before.getMntncYn(), after.getMntncYn());
        appendIfChanged(sb, "이메일발송", before.getEmailYn(), after.getEmailYn());
        if (sb.charAt(sb.length() - 1) == ':') {
            sb.append(" 변경 값 없음");
        }
        return sb.toString();
    }

    private void appendIfChanged(StringBuilder sb, String label, Object before, Object after) {
        boolean same = (before == null && after == null)
                || (before instanceof BigDecimal b && after instanceof BigDecimal a && b.compareTo(a) == 0)
                || (before != null && before.equals(after));
        if (!same) {
            sb.append(' ').append(label).append(' ').append(before).append('→').append(after).append(',');
        }
    }
}
