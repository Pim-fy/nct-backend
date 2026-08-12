package nct.ops.sanction.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.sanction.domain.ReportSanctionCreateCommand;
import nct.ops.sanction.domain.ReportSanctionReleaseCommand;
import nct.ops.sanction.domain.SanctionRecord;
import nct.ops.sanction.mapper.SanctionMapper;

/**
 * 담당자 7 · 신고 처리 제재: 신고별 계정 정지를 생성하고 기간제 정지의 해제 처리를 직렬화합니다.
 */
@Service
@RequiredArgsConstructor
public class ReportSanctionService {

    private static final int MAX_EXPIRED_BATCH_SIZE = 100;

    private final SanctionMapper sanctionMapper;

    @Transactional
    public SanctionRecord create(ReportSanctionCreateCommand command) {
        ReportSanctionCreateCommand valid = validateCreate(command);
        lockUser(valid.userSn());

        SanctionRecord existing = sanctionMapper.findBySourceReportForUpdate(valid.reportSn());
        if (existing != null) {
            requireSameTarget(existing, valid.userSn());
            return existing;
        }

        SanctionRecord retried = sanctionMapper.findByRestrictRequestId(valid.requestId());
        if (retried != null) {
            requireSameTarget(retried, valid.userSn());
            if (!valid.reportSn().equals(retried.getSourceReportSn())) {
                throw new CustomException(ErrorCode.CONFLICT, "다른 신고에 사용된 제재 요청 식별자입니다.");
            }
            return retried;
        }

        SanctionRecord sanction = new SanctionRecord();
        sanction.setSourceReportSn(valid.reportSn());
        sanction.setUserSn(valid.userSn());
        sanction.setProcessorUserSn(valid.adminUserSn());
        sanction.setReason(valid.reason());
        sanction.setRestrictRequestId(valid.requestId());
        sanction.setEndedAt(valid.endAt());
        try {
            if (sanctionMapper.insertReportAccountSuspension(sanction) != 1
                    || sanction.getSanctionSn() == null) {
                throw new CustomException(ErrorCode.CONFLICT, "신고 계정 제재를 생성하지 못했습니다.");
            }
            return sanctionMapper.findBySanctionIdForUpdate(sanction.getSanctionSn());
        } catch (DuplicateKeyException exception) {
            SanctionRecord concurrent = sanctionMapper.findBySourceReportForUpdate(valid.reportSn());
            if (concurrent == null) {
                throw new CustomException(ErrorCode.CONFLICT, "이미 사용된 신고 제재 요청입니다.");
            }
            requireSameTarget(concurrent, valid.userSn());
            return concurrent;
        }
    }

    @Transactional
    public ReleaseResult release(ReportSanctionReleaseCommand command) {
        ReportSanctionReleaseCommand valid = validateRelease(command);
        SanctionRecord sanction = sanctionMapper.findBySanctionIdForUpdate(valid.sanctionSn());
        if (sanction == null || sanction.getSourceReportSn() == null) {
            throw new CustomException(ErrorCode.NOT_FOUND, "신고로 생성된 계정 제재를 찾을 수 없습니다.");
        }
        if (sanction.getEndedAt() == null) {
            throw new CustomException(ErrorCode.CONFLICT, "영구 이용정지는 기간제 해제 기능으로 해제할 수 없습니다.");
        }
        lockUser(sanction.getUserSn());

        if (sanction.getReleaseRequestId() != null) {
            return new ReleaseResult(sanction, false);
        }

        boolean processExpired = valid.automatic()
                || !sanction.getEndedAt().isAfter(LocalDateTime.now());
        int updated = sanctionMapper.releaseTemporaryReportSanction(
                sanction.getSanctionSn(),
                valid.requestId(),
                String.valueOf(valid.adminUserSn()),
                processExpired);
        if (updated != 1) {
            SanctionRecord retried = sanctionMapper.findByReleaseRequestId(valid.requestId());
            if (retried != null && sanction.getSanctionSn().equals(retried.getSanctionSn())) {
                return new ReleaseResult(retried, false);
            }
            throw new CustomException(ErrorCode.CONFLICT, "이용정지 해제 상태가 이미 변경되었습니다.");
        }
        return new ReleaseResult(
                sanctionMapper.findBySanctionIdForUpdate(sanction.getSanctionSn()),
                true);
    }

    @Transactional(readOnly = true)
    public SanctionRecord findByReport(Long reportSn) {
        if (reportSn == null || reportSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return sanctionMapper.findBySourceReport(reportSn);
    }

    @Transactional
    public SanctionRecord findByIdForUpdate(Long sanctionSn) {
        if (sanctionSn == null || sanctionSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        SanctionRecord sanction = sanctionMapper.findBySanctionIdForUpdate(sanctionSn);
        if (sanction == null || sanction.getSourceReportSn() == null) {
            throw new CustomException(ErrorCode.NOT_FOUND, "신고 제재를 찾을 수 없습니다.");
        }
        return sanction;
    }

    @Transactional(readOnly = true)
    public boolean hasActiveSuspension(Long userSn) {
        if (userSn == null || userSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return sanctionMapper.existsActiveAccountSuspension(userSn);
    }

    @Transactional(readOnly = true)
    public List<Long> findExpiredUnprocessedIds(int limit) {
        if (limit <= 0 || limit > MAX_EXPIRED_BATCH_SIZE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return sanctionMapper.findExpiredUnprocessedReportSanctionIds(limit);
    }

    @Transactional(readOnly = true)
    public boolean hasOtherActiveSuspension(Long userSn, Long sanctionSn) {
        if (userSn == null || userSn <= 0 || sanctionSn == null || sanctionSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return sanctionMapper.countOtherActiveAccountSuspensions(userSn, sanctionSn) > 0;
    }

    private ReportSanctionCreateCommand validateCreate(ReportSanctionCreateCommand command) {
        if (command == null
                || command.reportSn() == null
                || command.reportSn() <= 0
                || command.userSn() == null
                || command.userSn() <= 0
                || command.adminUserSn() == null
                || command.adminUserSn() <= 0
                || command.reason() == null
                || command.reason().isBlank()
                || command.reason().trim().length() > 4000
                || command.requestId() == null
                || command.requestId().isBlank()
                || command.requestId().trim().length() > 100
                || (command.endAt() != null && !command.endAt().isAfter(LocalDateTime.now()))) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return new ReportSanctionCreateCommand(
                command.reportSn(),
                command.userSn(),
                command.adminUserSn(),
                command.reason().trim(),
                command.endAt(),
                command.requestId().trim());
    }

    private ReportSanctionReleaseCommand validateRelease(ReportSanctionReleaseCommand command) {
        if (command == null
                || command.sanctionSn() == null
                || command.sanctionSn() <= 0
                || command.adminUserSn() == null
                || command.adminUserSn() <= 0
                || command.reason() == null
                || command.reason().isBlank()
                || command.reason().trim().length() > 4000
                || command.requestId() == null
                || command.requestId().isBlank()
                || command.requestId().trim().length() > 100) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return new ReportSanctionReleaseCommand(
                command.sanctionSn(),
                command.adminUserSn(),
                command.reason().trim(),
                command.requestId().trim(),
                command.automatic());
    }

    private void lockUser(Long userSn) {
        if (sanctionMapper.lockUser(userSn) == null) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private void requireSameTarget(SanctionRecord sanction, Long userSn) {
        if (!userSn.equals(sanction.getUserSn())) {
            throw new CustomException(ErrorCode.CONFLICT, "다른 회원에게 연결된 신고 제재입니다.");
        }
    }

    public record ReleaseResult(SanctionRecord sanction, boolean changed) {
    }
}
