package nct.servicerequest.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.abuse.port.ReportTargetHoldPort;
import nct.abuse.port.ReportTargetHoldResult;
import nct.abuse.port.ReportTargetRestoreCommand;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.service.ReferenceDataService;
import nct.servicerequest.dto.ServiceRequestSanctionTarget;
import nct.servicerequest.mapper.ServiceRequestMapper;

/** 담당자 7 · F-OPS-007: 신고된 견적 요청 한 건만 운영 보류하고 남은 접수 시간을 복구합니다. */
@Service
@RequiredArgsConstructor
public class ServiceRequestReportTargetHoldService implements ReportTargetHoldPort {

    private static final String REFERENCE_TYPE = "REFC0007";
    private static final String STATUS_GROUP = "SVCG01";
    private static final String OPERATION_HOLD = "SVCC0005";
    private static final Set<String> PAUSABLE = Set.of("SVCC0001", "SVCC0002", "SVCC0003");

    private final ServiceRequestMapper serviceRequestMapper;
    private final ReferenceDataService referenceDataService;

    @Override
    public String referenceTypeCode() {
        return REFERENCE_TYPE;
    }

    @Override
    @Transactional
    public boolean lock(Long referenceSn) {
        if (referenceSn == null || referenceSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return serviceRequestMapper.findReportHoldTargetForUpdate(referenceSn) != null;
    }

    @Override
    @Transactional
    public ReportTargetHoldResult pause(Long referenceSn, String actorId) {
        validate(referenceSn, actorId);
        ServiceRequestSanctionTarget target =
                serviceRequestMapper.findReportHoldTargetForUpdate(referenceSn);
        if (target == null) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }
        String previous = target.getStatusCode();
        if (OPERATION_HOLD.equals(previous)) {
            return result(target, false, true, previous, "다른 신고로 이미 운영 보류 중입니다.");
        }
        if (!PAUSABLE.contains(previous)) {
            return result(target, false, false, previous, "현재 견적 요청 상태는 신고 접수 보류 대상이 아닙니다.");
        }

        referenceDataService.requireActiveCode(STATUS_GROUP, OPERATION_HOLD);
        if (serviceRequestMapper.pauseServiceRequestForSanction(
                referenceSn, previous, actorId) != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "견적 요청 상태가 변경되어 신고 보류를 적용할 수 없습니다.");
        }
        return result(target, true, false, previous, "신고 접수와 함께 해당 견적 요청을 운영 보류했습니다.");
    }

    @Override
    @Transactional
    public boolean restore(ReportTargetRestoreCommand command) {
        if (command == null
                || command.referenceSn() == null || command.referenceSn() <= 0
                || command.previousStatusCode() == null
                || !PAUSABLE.contains(command.previousStatusCode())
                || command.actorId() == null || command.actorId().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        referenceDataService.requireActiveCode(STATUS_GROUP, command.previousStatusCode());
        return serviceRequestMapper.restoreServiceRequestAfterSanction(
                command.referenceSn(),
                command.previousStatusCode(),
                nonNegative(command.remainingSeconds()),
                command.actorId()) == 1;
    }

    private ReportTargetHoldResult result(
            ServiceRequestSanctionTarget target,
            boolean changed,
            boolean alreadyOnReportHold,
            String previous,
            String message) {
        LocalDateTime now = target.getDatabaseNow() == null
                ? LocalDateTime.now()
                : target.getDatabaseNow();
        Long remaining = target.getEffectiveDeadlineAt() == null
                ? null
                : Math.max(0L, Duration.between(now, target.getEffectiveDeadlineAt()).getSeconds());
        return new ReportTargetHoldResult(
                target.getServiceRequestId(),
                changed,
                alreadyOnReportHold,
                previous,
                null,
                target.getEffectiveDeadlineAt(),
                null,
                remaining,
                false,
                message);
    }

    private Long nonNegative(Long value) {
        return value == null ? null : Math.max(0L, value);
    }

    private void validate(Long referenceSn, String actorId) {
        if (referenceSn == null || referenceSn <= 0 || actorId == null || actorId.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
