package nct.trade.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.crypto.FieldCryptoService;
import nct.trade.dto.TradeDetailResponse;
import nct.trade.dto.TradeOfflineScheduleProposal;
import nct.trade.dto.TradeOfflineScheduleRequest;
import nct.trade.dto.TradeOfflineTradeTarget;
import nct.trade.mapper.TradeMapper;
import nct.trade.mapper.TradeOfflineProposalMapper;

/** 직거래 일정의 제안·응답·확정 스냅샷 반영을 담당한다. */
@Service
@RequiredArgsConstructor
public class TradeOfflineScheduleProposalService {

    private static final String OFFLINE_METHOD = "TRDC0010";
    private static final String IN_PROGRESS = "TRDC0003";
    private static final String DELIVERING = "TRDC0004";
    private static final String COMPLETED = "TRDC0006";
    private static final String WAITING_CONFIRMATION = "TRDC0005";
    private static final String ON_HOLD = "TRDC0007";
    private static final String CANCELED = "TRDC0008";

    private static final String PENDING = "TRDC0025";
    private static final String ACCEPTED = "TRDC0026";
    private static final String REJECTED = "TRDC0027";
    private static final String WITHDRAWN = "TRDC0028";
    private static final String SCHEDULE = "TRDC0030";
    private static final String CHANGE = "TRDC0031";
    private static final String CANCEL = "TRDC0032";
    private static final int MAX_SCHEDULE_PROPOSALS_PER_PARTY = 3;

    private final TradeMapper tradeMapper;
    private final TradeOfflineProposalMapper proposalMapper;
    private final FieldCryptoService fieldCryptoService;

    /** 당사자 누구나 일정 신규 제안 또는 변경 제안을 등록한다. */
    @Transactional
    public void proposeSchedule(long tradeId, long userId, TradeOfflineScheduleRequest request) {
        validateSchedule(request);
        TradeOfflineTradeTarget target = requireTrade(tradeId, userId);
        validateNegotiableTrade(target);
        ensureNoPendingProposal(tradeId);
        ensureScheduleProposalLimit(tradeId, userId);

        TradeOfflineScheduleProposal proposal = new TradeOfflineScheduleProposal();
        proposal.setTradeId(tradeId);
        proposal.setProposalType(tradeMapper.hasOfflineSchedule(tradeId) ? CHANGE : SCHEDULE);
        proposal.setProposerUserId(userId);
        proposal.setMeetingDateTime(request.toMeetingDateTime());
        proposal.setMeetingPlace(request.getMeetingPlace().trim());
        proposal.setMeetingAddress(fieldCryptoService.encrypt(normalizeOptional(request.getMeetingAddress())));
        proposalMapper.insertProposal(proposal);
    }

    /** 확정 일정이 있는 거래에서 당사자 누구나 일정 취소를 제안한다. */
    @Transactional
    public void requestCancellation(long tradeId, long userId) {
        TradeOfflineTradeTarget target = requireTrade(tradeId, userId);
        validateNegotiableTrade(target);
        if (!tradeMapper.hasOfflineSchedule(tradeId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "취소할 확정 직거래 일정이 없습니다.");
        }
        ensureNoPendingProposal(tradeId);

        TradeOfflineScheduleProposal proposal = new TradeOfflineScheduleProposal();
        proposal.setTradeId(tradeId);
        proposal.setProposalType(CANCEL);
        proposal.setProposerUserId(userId);
        proposalMapper.insertProposal(proposal);
    }

    /** 대기 제안의 상대방만 수락 또는 거절할 수 있다. */
    @Transactional
    public void respond(long tradeId, long proposalId, long userId, boolean accept, String reason) {
        TradeOfflineTradeTarget target = requireTrade(tradeId, userId);
        validateNegotiableTrade(target);
        TradeOfflineScheduleProposal proposal = proposalMapper.findProposalForUpdate(tradeId, proposalId);

        if (proposal == null || !PENDING.equals(proposal.getProposalStatus())) {
            throw new CustomException(ErrorCode.NOT_FOUND,
                    "응답할 수 있는 일정 제안을 찾을 수 없습니다.");
        }
        if (proposal.getProposerUserId() == userId) {
            throw new CustomException(ErrorCode.FORBIDDEN,
                    "본인이 등록한 일정 제안에는 응답할 수 없습니다.");
        }

        String nextStatus = accept ? ACCEPTED : REJECTED;
        String normalizedReason = normalizeOptional(reason);
        if (normalizedReason != null && normalizedReason.length() > 4000) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "응답 사유는 4000자 이내로 입력해 주세요.");
        }
        if (proposalMapper.updateProposalStatus(proposalId, nextStatus, userId, normalizedReason) != 1) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "일정 제안 상태가 변경되어 다시 확인해 주세요.");
        }

        if (!accept) {
            return;
        }

        if (CANCEL.equals(proposal.getProposalType())) {
            tradeMapper.deleteOfflineSchedule(tradeId);
            if (tradeMapper.resetOfflineTrade(tradeId, String.valueOf(userId)) == 1) {
                tradeMapper.insertStatusHistory(
                        tradeId,
                        IN_PROGRESS,
                        "직거래 일정 취소가 상대방 동의로 확정되었습니다.");
            }
            return;
        }

        proposalMapper.supersedeAcceptedScheduleProposals(tradeId, proposalId);
        tradeMapper.upsertOfflineSchedule(
                tradeId,
                proposal.getMeetingDateTime(),
                proposal.getMeetingPlace(),
                proposal.getMeetingAddress());
        if (tradeMapper.startOfflineTrade(tradeId, String.valueOf(userId)) == 1) {
            tradeMapper.insertStatusHistory(
                    tradeId,
                    DELIVERING,
                    "직거래 일정 제안이 상대방 동의로 확정되었습니다.");
        }
    }

    /** 제안자 본인의 대기 중 제안만 철회한다. */
    @Transactional
    public void withdraw(long tradeId, long proposalId, long userId) {
        TradeOfflineTradeTarget target = requireTrade(tradeId, userId);
        validateNegotiableTrade(target);
        TradeOfflineScheduleProposal proposal = proposalMapper.findProposalForUpdate(tradeId, proposalId);

        if (proposal == null || !PENDING.equals(proposal.getProposalStatus())) {
            throw new CustomException(ErrorCode.NOT_FOUND,
                    "철회할 수 있는 일정 제안을 찾을 수 없습니다.");
        }
        if (proposal.getProposerUserId() != userId) {
            throw new CustomException(ErrorCode.FORBIDDEN,
                    "본인이 등록한 일정 제안만 철회할 수 있습니다.");
        }

        if (proposalMapper.updateProposalStatus(
                proposalId, WITHDRAWN, userId, "제안자가 일정을 철회했습니다.") != 1) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "일정 제안 상태가 변경되어 다시 시도해 주세요.");
        }
    }

    /** 거래 상세에 현재 확정 일정과 구분되는 대기 제안 정보를 붙인다. */
    @Transactional(readOnly = true)
    public void enrichDetail(TradeDetailResponse detail, long userId) {
        int scheduleProposalCount = proposalMapper.countScheduleProposalsByTradeAndProposer(
                detail.getTradeId(), userId);
        detail.setMyScheduleProposalCount(scheduleProposalCount);
        detail.setRemainingScheduleProposalCount(
                Math.max(0, MAX_SCHEDULE_PROPOSALS_PER_PARTY - scheduleProposalCount));

        TradeOfflineScheduleProposal pending = proposalMapper.findPendingProposal(detail.getTradeId());
        if (pending == null) {
            return;
        }

        detail.setPendingScheduleProposalId(pending.getProposalId());
        detail.setPendingScheduleProposalType(pending.getProposalType());
        detail.setPendingScheduleProposalStatus(pending.getProposalStatus());
        detail.setPendingScheduleProposalProposerRole(
                pending.getProposerUserId().equals(detail.getCounterpartUserId())
                        ? inverseRole(detail.getViewerRole())
                        : detail.getViewerRole());
        detail.setPendingMeetingDateTime(pending.getMeetingDateTime());
        detail.setPendingMeetingPlace(pending.getMeetingPlace());
        detail.setPendingMeetingAddress(fieldCryptoService.decrypt(pending.getMeetingAddress()));
        detail.setCanRespondToScheduleProposal(pending.getProposerUserId() != userId);
        detail.setCanWithdrawScheduleProposal(pending.getProposerUserId() == userId);
    }

    /** 일정 협의 이력은 거래 당사자만 열람한다. */
    @Transactional(readOnly = true)
    public List<TradeOfflineScheduleProposal> getHistory(long tradeId, long userId) {
        TradeOfflineTradeTarget target = proposalMapper.findMyOfflineTrade(tradeId, userId);
        if (target == null) {
            throw new CustomException(ErrorCode.NOT_FOUND,
                    "존재하지 않거나 접근할 수 없는 직거래입니다.");
        }

        return proposalMapper.findProposalHistory(tradeId).stream()
                .peek(proposal -> proposal.setMeetingAddress(
                        fieldCryptoService.decrypt(proposal.getMeetingAddress())))
                .toList();
    }

    private TradeOfflineTradeTarget requireTrade(long tradeId, long userId) {
        if (tradeId <= 0 || userId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "거래번호와 회원번호가 올바르지 않습니다.");
        }

        TradeOfflineTradeTarget target = proposalMapper.findMyOfflineTradeForUpdate(tradeId, userId);
        if (target == null) {
            throw new CustomException(ErrorCode.NOT_FOUND,
                    "존재하지 않거나 접근할 수 없는 직거래입니다.");
        }
        return target;
    }

    private void validateNegotiableTrade(TradeOfflineTradeTarget target) {
        if (!OFFLINE_METHOD.equals(target.getTradeMethod())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "직거래 방식의 거래만 일정 협의를 할 수 있습니다.");
        }
        if (COMPLETED.equals(target.getTradeStatus())
                || WAITING_CONFIRMATION.equals(target.getTradeStatus())
                || ON_HOLD.equals(target.getTradeStatus())
                || CANCELED.equals(target.getTradeStatus())) {
            throw new CustomException(ErrorCode.ALREADY_PROCESSED,
                    "현재 거래 상태에서는 일정 협의를 변경할 수 없습니다.");
        }
        if (!IN_PROGRESS.equals(target.getTradeStatus()) && !DELIVERING.equals(target.getTradeStatus())) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "현재 거래 상태에서는 일정 협의를 시작할 수 없습니다.");
        }
    }

    private void ensureNoPendingProposal(long tradeId) {
        if (proposalMapper.findPendingProposal(tradeId) != null) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "이미 응답을 기다리는 일정 제안이 있습니다.");
        }
    }

    private void ensureScheduleProposalLimit(long tradeId, long userId) {
        if (proposalMapper.countScheduleProposalsByTradeAndProposer(tradeId, userId)
                >= MAX_SCHEDULE_PROPOSALS_PER_PARTY) {
            throw new CustomException(ErrorCode.ALREADY_PROCESSED,
                    "직거래 일정 제안은 판매자와 구매자 각각 최초 제안을 포함해 최대 3회까지 할 수 있습니다.");
        }
    }

    private void validateSchedule(TradeOfflineScheduleRequest request) {
        if (request == null
                || request.getMeetingDate() == null
                || request.getMeetingTime() == null
                || request.getMeetingPlace() == null
                || request.getMeetingPlace().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "거래 일시와 장소를 입력해 주세요.");
        }
        if (!request.toMeetingDateTime().isAfter(java.time.LocalDateTime.now())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "거래 일시는 현재 시간 이후로 선택해 주세요.");
        }
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String inverseRole(String role) {
        return "BUYER".equals(role) ? "SELLER" : "BUYER";
    }
}
