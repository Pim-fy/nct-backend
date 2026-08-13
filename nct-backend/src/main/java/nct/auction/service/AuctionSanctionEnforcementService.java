package nct.auction.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.abuse.port.ActiveAbuseReportReferenceReader;
import nct.auction.constant.AuctionStatusCode;
import nct.auction.dto.AuctionSanctionTarget;
import nct.auction.mapper.AuctionMapper;
import nct.auction.port.AdminAuctionCancellationCommand;
import nct.auction.port.AdminAuctionCancellationPort;
import nct.auction.port.AdminAuctionCancellationResult;
import nct.auction.port.AuctionEnforcementImpact;
import nct.auction.port.AuctionEnforcementRestoreCommand;
import nct.auction.port.MemberAuctionEnforcementCommand;
import nct.auction.port.MemberAuctionEnforcementPort;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.service.ReferenceDataService;
import nct.point.service.PointService;
import nct.trade.port.ActiveTradeIncidentReader;

/**
 * 담당자 7 · 신고 처리 제재: 계정 정지와 연관된 경매를 운영보류·복구하거나 기존 관리자 취소 계약에 위임합니다.
 */
@Service
@RequiredArgsConstructor
public class AuctionSanctionEnforcementService implements MemberAuctionEnforcementPort {

    private static final String AUCTION_STATUS_GROUP = "AUCG01";
    private static final String TRADE_IN_PROGRESS = "TRDC0003";
    private static final Set<String> PAUSABLE = Set.of(
            AuctionStatusCode.READY,
            AuctionStatusCode.ACTIVE,
            AuctionStatusCode.CANCEL_REQUESTED);

    private final AuctionMapper auctionMapper;
    private final ReferenceDataService referenceDataService;
    private final AdminAuctionCancellationPort cancellationPort;
    private final PointService pointService;
    private final ActiveAbuseReportReferenceReader activeReportReferenceReader;
    private final ActiveTradeIncidentReader activeTradeIncidentReader;

    @Override
    @Transactional
    public List<AuctionEnforcementImpact> pause(MemberAuctionEnforcementCommand command) {
        MemberAuctionEnforcementCommand valid = validate(command, true);
        referenceDataService.requireActiveCode(
                AUCTION_STATUS_GROUP, AuctionStatusCode.OPERATION_HOLD);

        List<AuctionEnforcementImpact> impacts = new ArrayList<>();
        for (AuctionSanctionTarget target :
                auctionMapper.findSanctionTargetsByMemberForUpdate(valid.userSn())) {
            String previous = target.getAuctionStatusCode();
            String role = role(target, valid.userSn());
            boolean sellerOwned = role.startsWith("SELLER");
            boolean bidderOnly = "HIGHEST_BIDDER".equals(role);

            if (bidderOnly
                    && target.getHighestBidId() != null
                    && (PAUSABLE.contains(previous)
                            || AuctionStatusCode.OPERATION_HOLD.equals(previous)
                            || AuctionStatusCode.ADMIN_PAUSED.equals(previous))) {
                cancelHighestBid(target, valid.adminUserSn(), valid.reason());
                impacts.add(impact(
                        target,
                        valid.userSn(),
                        "BID_CANCELED",
                        previous,
                        "7일 이용정지 회원의 최고입찰만 취소하고 홀딩 포인트를 반환했습니다."));
                continue;
            }
            if (!sellerOwned) {
                continue;
            }
            if (AuctionStatusCode.OPERATION_HOLD.equals(previous)) {
                impacts.add(impact(
                        target, valid.userSn(), "PAUSED", previous, "다른 운영 제재로 이미 보류 중입니다."));
                continue;
            }
            if (!PAUSABLE.contains(previous)) {
                continue;
            }
            if (auctionMapper.pauseAuctionForSanction(
                    target.getAuctionId(), previous, String.valueOf(valid.adminUserSn())) != 1) {
                throw new CustomException(ErrorCode.CONFLICT, "경매 상태가 변경되어 운영보류할 수 없습니다.");
            }
            impacts.add(impact(
                    target, valid.userSn(), "PAUSED", previous, "7일 이용정지로 경매를 운영보류했습니다."));
        }
        return List.copyOf(impacts);
    }

    @Override
    @Transactional
    public List<AuctionEnforcementImpact> cancelForPermanentSuspension(
            MemberAuctionEnforcementCommand command) {
        MemberAuctionEnforcementCommand valid = validate(command, false);
        List<AuctionEnforcementImpact> impacts = new ArrayList<>();
        for (AuctionSanctionTarget target :
                auctionMapper.findSanctionTargetsByMemberForUpdate(valid.userSn())) {
            String status = target.getAuctionStatusCode();
            String role = role(target, valid.userSn());
            boolean safeEndedCancellation = AuctionStatusCode.ENDED.equals(status)
                    && (role.startsWith("SELLER") || "HIGHEST_BIDDER".equals(role))
                    && target.getTradeSn() != null
                    && Set.of(TRADE_IN_PROGRESS, "TRDC0007").contains(target.getTradeStatusCode());
            boolean safeHeldCancellation = AuctionStatusCode.OPERATION_HOLD.equals(status)
                    && role.startsWith("SELLER");
            boolean safeAdminPausedCancellation = AuctionStatusCode.ADMIN_PAUSED.equals(status)
                    && role.startsWith("SELLER");
            boolean sellerOwned = role.startsWith("SELLER");
            boolean bidderOnly = "HIGHEST_BIDDER".equals(role);

            if (hasOtherOpenIncident(target, valid.sourceReportSn())) {
                impacts.add(impact(
                        target,
                        valid.userSn(),
                        "HELD_FOR_REVIEW",
                        status,
                        "같은 경매 또는 거래의 다른 미해결 신고가 있어 자동 취소하지 않았습니다."));
                continue;
            }

            if (bidderOnly
                    && (PAUSABLE.contains(status)
                            || AuctionStatusCode.ADMIN_PAUSED.equals(status))) {
                cancelHighestBid(target, valid.adminUserSn(), valid.reason());
                impacts.add(impact(
                        target,
                        valid.userSn(),
                        "BID_CANCELED",
                        status,
                        "영구 이용정지 회원의 최고입찰만 취소하고 홀딩 포인트를 반환했습니다."));
                continue;
            }

            if ((!sellerOwned || !PAUSABLE.contains(status))
                    && !safeEndedCancellation
                    && !safeHeldCancellation
                    && !safeAdminPausedCancellation) {
                if (AuctionStatusCode.OPERATION_HOLD.equals(status)
                        || AuctionStatusCode.ENDED.equals(status)) {
                    impacts.add(impact(
                            target,
                            valid.userSn(),
                            "HELD_FOR_REVIEW",
                            status,
                            "이행 또는 다른 제재 상태를 확인해야 해 자동 취소하지 않았습니다."));
                }
                continue;
            }

            AdminAuctionCancellationResult result = cancellationPort.cancel(
                    new AdminAuctionCancellationCommand(
                            target.getAuctionId(),
                            valid.adminUserSn(),
                            valid.reason(),
                            childRequestId(valid.requestId(), target.getAuctionId()),
                            valid.sourceReportSn()));
            impacts.add(impact(
                    target,
                    valid.userSn(),
                    "CANCELED",
                    result.previousStatusCode(),
                    result.changed() ? "영구 이용정지로 경매를 취소했습니다." : "이미 취소된 경매입니다."));
        }
        return List.copyOf(impacts);
    }

    private boolean hasOtherOpenIncident(AuctionSanctionTarget target, Long sourceReportSn) {
        if (activeReportReferenceReader.hasOtherActiveReportLinkedToAuction(
                target.getAuctionId(), sourceReportSn)) {
            return true;
        }
        return target.getTradeSn() != null
                && activeTradeIncidentReader.hasOtherOpenIncident(
                        target.getTradeSn(), sourceReportSn);
    }

    private void cancelHighestBid(
            AuctionSanctionTarget target,
            Long adminUserSn,
            String reason) {
        if (target.getHighestBidId() == null || target.getHighestBidderUserSn() == null) {
            throw new CustomException(ErrorCode.CONFLICT, "취소할 최고입찰 정보를 확인할 수 없습니다.");
        }
        if (auctionMapper.exceptionCancelHighestBid(
                target.getAuctionId(),
                target.getHighestBidId(),
                String.valueOf(adminUserSn)) != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "최고입찰 상태가 변경되어 취소할 수 없습니다.");
        }
        pointService.releaseHold(
                target.getHighestBidderUserSn(),
                RefType.BID,
                target.getHighestBidId(),
                "영구 이용정지 입찰 홀딩 반환: " + reason);
    }

    @Override
    @Transactional
    public boolean restore(AuctionEnforcementRestoreCommand command) {
        if (command == null
                || command.auctionId() == null
                || command.auctionId() <= 0
                || command.adminUserSn() == null
                || command.adminUserSn() <= 0
                || !PAUSABLE.contains(command.previousStatusCode())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        referenceDataService.requireActiveCode(AUCTION_STATUS_GROUP, command.previousStatusCode());
        return auctionMapper.restoreAuctionAfterSanction(
                command.auctionId(),
                command.previousStatusCode(),
                nonNegative(command.remainingStartSeconds()),
                nonNegative(command.remainingSeconds()),
                String.valueOf(command.adminUserSn())) == 1;
    }

    private AuctionEnforcementImpact impact(
            AuctionSanctionTarget target,
            Long restrictedUserSn,
            String action,
            String previousStatus,
            String result) {
        LocalDateTime now = target.getDatabaseNow() == null
                ? LocalDateTime.now()
                : target.getDatabaseNow();
        return new AuctionEnforcementImpact(
                target.getAuctionId(),
                role(target, restrictedUserSn),
                action,
                previousStatus,
                target.getStartAt(),
                target.getEndAt(),
                remaining(now, target.getStartAt()),
                remaining(now, target.getEndAt()),
                result);
    }

    private String role(AuctionSanctionTarget target, Long restrictedUserSn) {
        boolean seller = restrictedUserSn.equals(target.getSellerUserSn());
        boolean bidder = restrictedUserSn.equals(target.getHighestBidderUserSn());
        if (seller && bidder) {
            return "SELLER_AND_HIGHEST_BIDDER";
        }
        if (seller) return "SELLER";
        if (bidder) return "HIGHEST_BIDDER";
        return "RELATED";
    }

    private Long remaining(LocalDateTime now, LocalDateTime deadline) {
        return deadline == null ? null : Math.max(0L, Duration.between(now, deadline).getSeconds());
    }

    private Long nonNegative(Long value) {
        return value == null ? null : Math.max(0L, value);
    }

    private String childRequestId(String requestId, Long auctionId) {
        UUID fingerprint = UUID.nameUUIDFromBytes(
                (requestId + "|auction|" + auctionId).getBytes(StandardCharsets.UTF_8));
        return "report-sanction-auction:" + fingerprint;
    }

    private MemberAuctionEnforcementCommand validate(
            MemberAuctionEnforcementCommand command,
            boolean requireReleaseAt) {
        if (command == null
                || command.userSn() == null
                || command.userSn() <= 0
                || command.adminUserSn() == null
                || command.adminUserSn() <= 0
                || command.reason() == null
                || command.reason().isBlank()
                || command.reason().trim().length() > 1000
                || command.requestId() == null
                || command.requestId().isBlank()
                || command.requestId().trim().length() > 100
                || (command.sourceReportSn() != null && command.sourceReportSn() <= 0)
                || (requireReleaseAt && command.releaseAt() == null)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return new MemberAuctionEnforcementCommand(
                command.userSn(),
                command.adminUserSn(),
                command.reason().trim(),
                command.requestId().trim(),
                command.releaseAt(),
                command.sourceReportSn());
    }
}
