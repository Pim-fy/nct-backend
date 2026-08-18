package nct.setting.adapter;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.risk.port.RiskDetectionPolicy;
import nct.ops.risk.port.RiskDetectionPolicyReader;
import nct.setting.domain.SystemSettingDetail;
import nct.setting.mapper.SystemSettingAdminMapper;

/** 담당자 7 · REQ-OPS-011: 설정 단일 행을 리스크 판정 계약으로 변환합니다. */
@Component
@RequiredArgsConstructor
public class SystemSettingRiskDetectionPolicyAdapter implements RiskDetectionPolicyReader {

    private final SystemSettingAdminMapper settingMapper;

    @Override
    public RiskDetectionPolicy getPolicy() {
        SystemSettingDetail setting = settingMapper.selectOne();
        if (setting == null) {
            throw new CustomException(ErrorCode.DATABASE_ERROR, "시스템 설정을 찾을 수 없습니다.");
        }
        return new RiskDetectionPolicy(
                setting.getRiskTradeReportCount(),
                setting.getRiskTradeReportWindowMinutes(),
                setting.getRiskSettlementHoldDays(),
                setting.getRiskRepeatReportCount(),
                setting.getRiskRepeatReportWindowDays(),
                setting.getRiskAdminLoginFailCount(),
                setting.getRiskAdminLoginFailWindowMinutes());
    }
}
