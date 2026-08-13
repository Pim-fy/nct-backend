package nct.auction.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.auction.constant.AuctionStatusCode;
import nct.auction.dto.AuctionBidTarget;
import nct.auction.mapper.AuctionMapper;
import nct.auction.port.AdminAuctionPauseCommand;
import nct.auction.port.AdminAuctionPausePort;
import nct.auction.port.AdminAuctionPauseResult;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.service.ReferenceDataService;
import nct.ops.sanction.port.SanctionStatusReader;

/**
 * 담당자 7 · F-OPS-003: 관리자 수동 일시중지 상태를 제재 운영보류와 분리해 처리합니다.
 */
@Service
@RequiredArgsConstructor
public class AdminAuctionPauseService implements AdminAuctionPausePort {

    private static final String AUCTION_STATUS_GROUP = "AUCG01";

    private final AuctionMapper auctionMapper;
    private final ReferenceDataService referenceDataService;
    private final SanctionStatusReader sanctionStatusReader;

    @Override
    @Transactional
    public AdminAuctionPauseResult pause(AdminAuctionPauseCommand command) {
        AdminAuctionPauseCommand valid = validate(command);
        AuctionBidTarget target = lock(valid.auctionId());
        String previousStatus = target.getAuctionStatusCode();
        if (AuctionStatusCode.ADMIN_PAUSED.equals(previousStatus)) {
            return result(target, previousStatus, previousStatus, false);
        }
        if (!AuctionStatusCode.ACTIVE.equals(previousStatus)) {
            throw new CustomException(ErrorCode.CONFLICT, "진행 중인 경매만 일시중지할 수 있습니다.");
        }
        LocalDateTime databaseNow = target.getDatabaseNow() == null
                ? LocalDateTime.now()
                : target.getDatabaseNow();
        if (target.getEndDateTime() != null && !target.getEndDateTime().isAfter(databaseNow)) {
            throw new CustomException(ErrorCode.CONFLICT, "종료 시각이 지난 경매는 일시중지할 수 없습니다.");
        }
        referenceDataService.requireActiveCode(
                AUCTION_STATUS_GROUP,
                AuctionStatusCode.ADMIN_PAUSED);
        if (auctionMapper.pauseAuctionForAdmin(
                valid.auctionId(), String.valueOf(valid.adminUserId())) != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "경매 상태가 이미 변경되었습니다.");
        }
        return result(target, previousStatus, AuctionStatusCode.ADMIN_PAUSED, true);
    }

    @Override
    @Transactional
    public AdminAuctionPauseResult resume(AdminAuctionPauseCommand command) {
        AdminAuctionPauseCommand valid = validate(command);
        AuctionBidTarget target = lock(valid.auctionId());
        String previousStatus = target.getAuctionStatusCode();
        if (AuctionStatusCode.ACTIVE.equals(previousStatus)) {
            return result(target, previousStatus, previousStatus, false);
        }
        if (!AuctionStatusCode.ADMIN_PAUSED.equals(previousStatus)) {
            throw new CustomException(
                    ErrorCode.CONFLICT,
                    "관리자가 수동 일시중지한 경매만 재개할 수 있습니다.");
        }
        sanctionStatusReader.requireNoActiveSanction(target.getSellerId());
        if (target.getCurrentHighestBidderId() != null) {
            sanctionStatusReader.requireNoActiveSanction(target.getCurrentHighestBidderId());
        }
        referenceDataService.requireActiveCode(AUCTION_STATUS_GROUP, AuctionStatusCode.ACTIVE);
        if (auctionMapper.resumeAuctionAfterAdminPause(
                valid.auctionId(), String.valueOf(valid.adminUserId())) != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "경매 상태가 이미 변경되었습니다.");
        }
        return result(target, previousStatus, AuctionStatusCode.ACTIVE, true);
    }

    private AdminAuctionPauseCommand validate(AdminAuctionPauseCommand command) {
        if (command == null
                || command.auctionId() == null
                || command.auctionId() <= 0
                || command.adminUserId() == null
                || command.adminUserId() <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return command;
    }

    private AuctionBidTarget lock(Long auctionId) {
        AuctionBidTarget target = auctionMapper.findAuctionBidTargetForUpdate(auctionId);
        if (target == null) {
            throw new CustomException(ErrorCode.AUCTION_NOT_FOUND);
        }
        return target;
    }

    private AdminAuctionPauseResult result(
            AuctionBidTarget target,
            String previousStatus,
            String status,
            boolean changed) {
        return new AdminAuctionPauseResult(
                target.getAuctionId(),
                target.getSellerId(),
                previousStatus,
                status,
                changed);
    }
}
