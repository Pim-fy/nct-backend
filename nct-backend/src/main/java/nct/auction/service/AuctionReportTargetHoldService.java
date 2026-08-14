package nct.auction.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.abuse.port.ReportTargetHoldPort;
import nct.abuse.port.ReportTargetHoldResult;
import nct.abuse.port.ReportTargetRestoreCommand;
import nct.auction.constant.AuctionStatusCode;
import nct.auction.dto.AuctionSanctionTarget;
import nct.auction.mapper.AuctionMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.service.ReferenceDataService;

/** 담당자 7 · F-OPS-007: 신고된 경매 한 건만 운영 보류하고 남은 경매 시간을 복구합니다. */
@Service
@RequiredArgsConstructor
public class AuctionReportTargetHoldService implements ReportTargetHoldPort {

    private static final String REFERENCE_TYPE = "REFC0003";
    private static final String STATUS_GROUP = "AUCG01";
    private static final Set<String> PAUSABLE = Set.of(
            AuctionStatusCode.READY,
            AuctionStatusCode.ACTIVE,
            AuctionStatusCode.CANCEL_REQUESTED,
            AuctionStatusCode.ADMIN_PAUSED);

    private final AuctionMapper auctionMapper;
    private final ReferenceDataService referenceDataService;

    @Override
    public String referenceTypeCode() {
        return REFERENCE_TYPE;
    }

    @Override
    @Transactional
    public ReportTargetHoldResult pause(Long referenceSn, String actorId) {
        validate(referenceSn, actorId);
        AuctionSanctionTarget target = auctionMapper.findReportHoldTargetForUpdate(referenceSn);
        if (target == null) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }
        String previous = target.getAuctionStatusCode();
        if (AuctionStatusCode.OPERATION_HOLD.equals(previous)) {
            return result(target, false, true, previous, "다른 신고로 이미 운영 보류 중입니다.");
        }
        if (!PAUSABLE.contains(previous)) {
            return result(target, false, false, previous, "현재 경매 상태는 신고 접수 보류 대상이 아닙니다.");
        }

        referenceDataService.requireActiveCode(STATUS_GROUP, AuctionStatusCode.OPERATION_HOLD);
        if (auctionMapper.pauseAuctionForSanction(referenceSn, previous, actorId) != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "경매 상태가 변경되어 신고 보류를 적용할 수 없습니다.");
        }
        return result(target, true, false, previous, "신고 접수와 함께 해당 경매를 운영 보류했습니다.");
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
        return auctionMapper.restoreAuctionAfterSanction(
                command.referenceSn(),
                command.previousStatusCode(),
                nonNegative(command.remainingStartSeconds()),
                nonNegative(command.remainingSeconds()),
                command.actorId()) == 1;
    }

    private ReportTargetHoldResult result(
            AuctionSanctionTarget target,
            boolean changed,
            boolean alreadyOnReportHold,
            String previous,
            String message) {
        LocalDateTime now = target.getDatabaseNow() == null
                ? LocalDateTime.now()
                : target.getDatabaseNow();
        LocalDateTime remainingBase = AuctionStatusCode.ADMIN_PAUSED.equals(previous)
                && target.getUpdatedAt() != null
                        ? target.getUpdatedAt()
                        : now;
        return new ReportTargetHoldResult(
                target.getAuctionId(),
                changed,
                alreadyOnReportHold,
                previous,
                target.getStartAt(),
                target.getEndAt(),
                remaining(now, target.getStartAt()),
                remaining(remainingBase, target.getEndAt()),
                message);
    }

    private Long remaining(LocalDateTime from, LocalDateTime until) {
        if (from == null || until == null) {
            return null;
        }
        return Math.max(0L, Duration.between(from, until).getSeconds());
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
