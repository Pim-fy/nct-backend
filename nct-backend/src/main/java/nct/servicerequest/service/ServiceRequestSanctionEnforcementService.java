package nct.servicerequest.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.service.ReferenceDataService;
import nct.servicerequest.dto.ServiceRequestSanctionTarget;
import nct.servicerequest.mapper.ServiceRequestMapper;
import nct.servicerequest.port.MemberServiceRequestEnforcementCommand;
import nct.servicerequest.port.MemberServiceRequestEnforcementPort;
import nct.servicerequest.port.ServiceRequestEnforcementImpact;
import nct.servicerequest.port.ServiceRequestEnforcementRestoreCommand;

/**
 * 담당자 7 · 신고 처리 제재: 서비스 요청서를 운영보류하고 남은 기간으로 복구하거나 영구정지 시 종료합니다.
 */
@Service
@RequiredArgsConstructor
public class ServiceRequestSanctionEnforcementService
        implements MemberServiceRequestEnforcementPort {

    private static final String STATUS_GROUP = "SVCG01";
    private static final String DRAFT = "SVCC0001";
    private static final String OPEN = "SVCC0002";
    private static final String MATCHED = "SVCC0003";
    private static final String CLOSED = "SVCC0004";
    private static final String OPERATION_HOLD = "SVCC0005";
    private static final String TRADE_CANCELED = "TRDC0008";
    private static final Set<String> RESTORABLE = Set.of(DRAFT, OPEN, MATCHED);

    private final ServiceRequestMapper serviceRequestMapper;
    private final ReferenceDataService referenceDataService;

    @Override
    @Transactional
    public List<ServiceRequestEnforcementImpact> pauseOwned(
            MemberServiceRequestEnforcementCommand command) {
        MemberServiceRequestEnforcementCommand valid = validate(command, true);
        referenceDataService.requireActiveCode(STATUS_GROUP, OPERATION_HOLD);

        List<ServiceRequestEnforcementImpact> impacts = new ArrayList<>();
        for (ServiceRequestSanctionTarget target :
                serviceRequestMapper.findSanctionTargetsByOwnerForUpdate(valid.userSn())) {
            String previous = target.getStatusCode();
            if (OPERATION_HOLD.equals(previous)) {
                impacts.add(impact(target, "PAUSED", previous, "다른 운영 제재로 이미 보류 중입니다."));
                continue;
            }
            if (!RESTORABLE.contains(previous)) {
                continue;
            }
            if (serviceRequestMapper.pauseServiceRequestForSanction(
                    target.getServiceRequestId(), previous, String.valueOf(valid.adminUserSn())) != 1) {
                throw new CustomException(ErrorCode.CONFLICT, "요청서 상태가 변경되어 운영보류할 수 없습니다.");
            }
            impacts.add(impact(target, "PAUSED", previous, "7일 이용정지로 요청서를 운영보류했습니다."));
        }
        return List.copyOf(impacts);
    }

    @Override
    @Transactional
    public List<ServiceRequestEnforcementImpact> cancelOwnedForPermanentSuspension(
            MemberServiceRequestEnforcementCommand command) {
        MemberServiceRequestEnforcementCommand valid = validate(command, false);
        referenceDataService.requireActiveCode(STATUS_GROUP, CLOSED);

        List<ServiceRequestEnforcementImpact> impacts = new ArrayList<>();
        for (ServiceRequestSanctionTarget target :
                serviceRequestMapper.findSanctionTargetsByOwnerForUpdate(valid.userSn())) {
            String previous = target.getStatusCode();
            if (DRAFT.equals(previous)) {
                requireChanged(serviceRequestMapper.closeDraftServiceRequestForSanction(
                        target.getServiceRequestId(), String.valueOf(valid.adminUserSn())));
                impacts.add(impact(target, "CANCELED", previous, "영구 이용정지로 임시 요청서를 종료했습니다."));
            } else if (OPEN.equals(previous)) {
                requireChanged(serviceRequestMapper.adminCancelOpenServiceRequest(
                        target.getServiceRequestId(), String.valueOf(valid.adminUserSn())));
                impacts.add(impact(target, "CANCELED", previous, "영구 이용정지로 공개 요청서를 종료했습니다."));
            } else if (MATCHED.equals(previous) || OPERATION_HOLD.equals(previous)) {
                boolean linkedTradeCanceled = target.getLinkedTradeSn() != null
                        && TRADE_CANCELED.equals(target.getLinkedTradeStatusCode());
                if (linkedTradeCanceled) {
                    requireChanged(serviceRequestMapper.closeMatchedOrHeldServiceRequestForSanction(
                            target.getServiceRequestId(),
                            previous,
                            String.valueOf(valid.adminUserSn())));
                    impacts.add(impact(
                            target,
                            "CANCELED",
                            previous,
                            "연결 거래 취소를 확인하고 매칭 요청서를 종료했습니다."));
                } else {
                    impacts.add(impact(
                            target,
                            "HELD_FOR_REVIEW",
                            previous,
                            "연결 거래의 분쟁 또는 진행 상태를 확인해야 해 요청서를 자동 종료하지 않았습니다."));
                }
            }
        }
        return List.copyOf(impacts);
    }

    @Override
    @Transactional
    public boolean restore(ServiceRequestEnforcementRestoreCommand command) {
        if (command == null
                || command.serviceRequestId() == null
                || command.serviceRequestId() <= 0
                || command.adminUserSn() == null
                || command.adminUserSn() <= 0
                || !RESTORABLE.contains(command.previousStatusCode())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        referenceDataService.requireActiveCode(STATUS_GROUP, command.previousStatusCode());
        return serviceRequestMapper.restoreServiceRequestAfterSanction(
                command.serviceRequestId(),
                command.previousStatusCode(),
                command.remainingSeconds() == null ? null : Math.max(0L, command.remainingSeconds()),
                String.valueOf(command.adminUserSn())) == 1;
    }

    private ServiceRequestEnforcementImpact impact(
            ServiceRequestSanctionTarget target,
            String action,
            String previous,
            String result) {
        LocalDateTime now = target.getDatabaseNow() == null
                ? LocalDateTime.now()
                : target.getDatabaseNow();
        Long remaining = target.getEffectiveDeadlineAt() == null
                ? null
                : Math.max(0L, Duration.between(now, target.getEffectiveDeadlineAt()).getSeconds());
        return new ServiceRequestEnforcementImpact(
                target.getServiceRequestId(),
                action,
                previous,
                target.getEffectiveDeadlineAt(),
                remaining,
                result);
    }

    private void requireChanged(int changed) {
        if (changed != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "요청서 상태가 이미 변경되었습니다.");
        }
    }

    private MemberServiceRequestEnforcementCommand validate(
            MemberServiceRequestEnforcementCommand command,
            boolean requireReleaseAt) {
        if (command == null
                || command.userSn() == null
                || command.userSn() <= 0
                || command.adminUserSn() == null
                || command.adminUserSn() <= 0
                || command.reason() == null
                || command.reason().isBlank()
                || (requireReleaseAt && command.releaseAt() == null)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return new MemberServiceRequestEnforcementCommand(
                command.userSn(),
                command.adminUserSn(),
                command.reason().trim(),
                command.releaseAt());
    }
}
