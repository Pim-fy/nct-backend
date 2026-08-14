package nct.abuse.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nct.abuse.domain.ReportImpactRecord;
import nct.abuse.mapper.ReportImpactMapper;
import nct.abuse.port.ReportTargetHoldPort;
import nct.abuse.port.ReportTargetHoldResult;
import nct.abuse.port.ReportTargetRestoreCommand;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

/** 담당자 7 · F-OPS-007: 신고 대상 단건 보류의 기준 상태와 마지막 신고 해소 시 복구를 조정합니다. */
@Service
public class ReportTargetHoldService {

    private static final String RECEIVED_STATUS = "ABSC0001";
    private static final String PROCESSING_STATUS = "ABSC0002";
    private static final String APPLIED = "APPLIED";
    private static final String SKIPPED = "SKIPPED";
    private static final String RESTORED = "RESTORED";
    private static final String RETAINED = "RETAINED";

    private final ReportImpactMapper reportImpactMapper;
    private final Map<String, ReportTargetHoldPort> ports;

    public ReportTargetHoldService(
            ReportImpactMapper reportImpactMapper,
            List<ReportTargetHoldPort> ports) {
        this.reportImpactMapper = reportImpactMapper;
        this.ports = ports.stream().collect(Collectors.toUnmodifiableMap(
                ReportTargetHoldPort::referenceTypeCode,
                Function.identity()));
    }

    @Transactional
    public void pause(Long reportSn, String referenceTypeCode, Long referenceSn, String actorId) {
        if (reportSn == null || reportSn <= 0
                || referenceTypeCode == null || referenceTypeCode.isBlank()
                || referenceSn == null || referenceSn <= 0
                || actorId == null || actorId.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        ReportTargetHoldPort port = ports.get(referenceTypeCode);
        if (port == null) {
            return;
        }

        ReportTargetHoldResult hold = port.pause(referenceSn, actorId);
        if (hold == null) {
            return;
        }

        ReportImpactRecord baseline = hold.alreadyOnReportHold()
                ? reportImpactMapper.findActiveBaselineForUpdate(
                        referenceTypeCode,
                        referenceSn,
                        RECEIVED_STATUS,
                        PROCESSING_STATUS)
                : null;
        boolean restorable = hold.changed() || baseline != null;
        ReportImpactRecord impact = restorable
                ? appliedImpact(reportSn, referenceTypeCode, hold, baseline, actorId)
                : skippedImpact(reportSn, referenceTypeCode, hold, actorId);
        if (reportImpactMapper.insert(impact) != 1) {
            throw new CustomException(ErrorCode.DATABASE_ERROR);
        }
    }

    @Transactional
    public void release(Long reportSn, String actorId) {
        if (reportSn == null || reportSn <= 0 || actorId == null || actorId.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        ReportImpactRecord impact = reportImpactMapper.findByReportForUpdate(reportSn);
        if (impact == null || !APPLIED.equals(impact.getStatusCode())) {
            return;
        }
        if (reportImpactMapper.existsOtherActiveImpact(
                impact.getReferenceTypeCode(),
                impact.getReferenceSn(),
                reportSn,
                RECEIVED_STATUS,
                PROCESSING_STATUS)) {
            update(impact, RETAINED, "같은 대상의 다른 신고가 처리 중이어서 운영 보류를 유지했습니다.", actorId);
            return;
        }

        ReportTargetHoldPort port = ports.get(impact.getReferenceTypeCode());
        if (port == null) {
            update(impact, SKIPPED, "복구 계약을 찾을 수 없어 현재 상태를 유지했습니다.", actorId);
            return;
        }
        boolean restored = port.restore(new ReportTargetRestoreCommand(
                impact.getReferenceSn(),
                impact.getPreviousStatusCode(),
                impact.getRemainingStartSeconds(),
                impact.getRemainingSeconds(),
                actorId));
        update(
                impact,
                restored ? RESTORED : SKIPPED,
                restored
                        ? "마지막 활성 신고가 해소되어 보류 전 상태와 남은 시간을 복구했습니다."
                        : "대상 상태가 이미 변경되어 신고 보류 복구를 적용하지 않았습니다.",
                actorId);
    }

    private ReportImpactRecord appliedImpact(
            Long reportSn,
            String referenceTypeCode,
            ReportTargetHoldResult hold,
            ReportImpactRecord baseline,
            String actorId) {
        return ReportImpactRecord.builder()
                .reportSn(reportSn)
                .referenceTypeCode(referenceTypeCode)
                .referenceSn(hold.referenceSn())
                .actionCode("PAUSED")
                .statusCode(APPLIED)
                .previousStatusCode(baseline == null
                        ? hold.previousStatusCode()
                        : baseline.getPreviousStatusCode())
                .previousStartAt(baseline == null
                        ? hold.previousStartAt()
                        : baseline.getPreviousStartAt())
                .previousDeadlineAt(baseline == null
                        ? hold.previousDeadlineAt()
                        : baseline.getPreviousDeadlineAt())
                .remainingStartSeconds(baseline == null
                        ? hold.remainingStartSeconds()
                        : baseline.getRemainingStartSeconds())
                .remainingSeconds(baseline == null
                        ? hold.remainingSeconds()
                        : baseline.getRemainingSeconds())
                .settlementHoldApplied(false)
                .result(hold.result())
                .registeredBy(actorId)
                .updatedBy(actorId)
                .build();
    }

    private ReportImpactRecord skippedImpact(
            Long reportSn,
            String referenceTypeCode,
            ReportTargetHoldResult hold,
            String actorId) {
        return ReportImpactRecord.builder()
                .reportSn(reportSn)
                .referenceTypeCode(referenceTypeCode)
                .referenceSn(hold.referenceSn())
                .actionCode("NONE")
                .statusCode(SKIPPED)
                .previousStatusCode(hold.previousStatusCode())
                .previousStartAt(hold.previousStartAt())
                .previousDeadlineAt(hold.previousDeadlineAt())
                .remainingStartSeconds(hold.remainingStartSeconds())
                .remainingSeconds(hold.remainingSeconds())
                .settlementHoldApplied(false)
                .result(hold.result())
                .registeredBy(actorId)
                .updatedBy(actorId)
                .build();
    }

    private void update(
            ReportImpactRecord impact,
            String statusCode,
            String result,
            String actorId) {
        if (reportImpactMapper.updateResult(
                impact.getImpactSn(), APPLIED, statusCode, result, actorId) != 1) {
            throw new CustomException(ErrorCode.CONFLICT);
        }
    }
}
