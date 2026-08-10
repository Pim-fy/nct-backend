package nct.ops.sanction.service;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.member.port.AccountSanctionCommand;
import nct.ops.member.port.AccountSanctionHistory;
import nct.ops.member.port.AccountSanctionPort;
import nct.ops.sanction.domain.SanctionRecord;
import nct.ops.sanction.mapper.SanctionMapper;

/** F-OPS-002/019 회원 제한·해제를 SANCTION 소유 경계에서 처리하는 구현체입니다. */
@Service
@RequiredArgsConstructor
public class AccountSanctionService implements AccountSanctionPort {

    private static final int MAX_HISTORY_LIMIT = 100;

    private final SanctionMapper sanctionMapper;

    @Override
    @Transactional
    public void restrict(AccountSanctionCommand command) {
        AccountSanctionCommand valid = validate(command);
        lockUser(valid.userSn());

        SanctionRecord retried = sanctionMapper.findByRestrictRequestId(valid.requestId());
        if (retried != null) {
            requireSameUser(retried, valid.userSn());
            return;
        }

        if (!sanctionMapper.findActiveAccountSuspensionsForUpdate(valid.userSn()).isEmpty()) {
            return;
        }

        try {
            int inserted = sanctionMapper.insertAccountSuspension(
                    valid.userSn(),
                    valid.adminUserSn(),
                    valid.reason(),
                    valid.requestId(),
                    String.valueOf(valid.adminUserSn()));
            if (inserted != 1) {
                throw new CustomException(ErrorCode.CONFLICT, "계정 제재를 생성하지 못했습니다.");
            }
        } catch (DuplicateKeyException exception) {
            SanctionRecord concurrentRetry = sanctionMapper.findByRestrictRequestId(valid.requestId());
            if (concurrentRetry == null) {
                throw new CustomException(ErrorCode.CONFLICT, "이미 사용된 제재 요청 식별자입니다.");
            }
            requireSameUser(concurrentRetry, valid.userSn());
        }
    }

    @Override
    @Transactional
    public void release(AccountSanctionCommand command) {
        AccountSanctionCommand valid = validate(command);
        lockUser(valid.userSn());

        SanctionRecord retried = sanctionMapper.findByReleaseRequestId(valid.requestId());
        if (retried != null) {
            requireSameUser(retried, valid.userSn());
            return;
        }

        List<SanctionRecord> active = sanctionMapper.findActiveAccountSuspensionsForUpdate(valid.userSn());
        if (active.isEmpty()) {
            return;
        }

        for (int index = 0; index < active.size(); index++) {
            SanctionRecord sanction = active.get(index);
            String requestId = index == 0 ? valid.requestId() : null;
            try {
                int updated = sanctionMapper.releaseAccountSuspension(
                        sanction.getSanctionSn(), requestId, String.valueOf(valid.adminUserSn()));
                if (updated != 1) {
                    throw new CustomException(ErrorCode.CONFLICT, "계정 제재 상태가 이미 변경되었습니다.");
                }
            } catch (DuplicateKeyException exception) {
                SanctionRecord concurrentRetry = sanctionMapper.findByReleaseRequestId(valid.requestId());
                if (concurrentRetry == null) {
                    throw new CustomException(ErrorCode.CONFLICT, "이미 사용된 제재 해제 요청 식별자입니다.");
                }
                requireSameUser(concurrentRetry, valid.userSn());
                return;
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountSanctionHistory> findHistory(Long userSn, int limit) {
        if (userSn == null || userSn <= 0 || limit <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return sanctionMapper.findHistory(userSn, Math.min(limit, MAX_HISTORY_LIMIT)).stream()
                .map(this::history)
                .toList();
    }

    private AccountSanctionHistory history(SanctionRecord record) {
        return new AccountSanctionHistory(
                record.getSanctionSn(),
                record.getSanctionTypeCode(),
                record.getSanctionTypeName(),
                record.getReason(),
                record.getStartedAt(),
                record.getEndedAt(),
                record.getProcessorUserSn());
    }

    private AccountSanctionCommand validate(AccountSanctionCommand command) {
        if (command == null
                || command.userSn() == null
                || command.userSn() <= 0
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
        return new AccountSanctionCommand(
                command.userSn(),
                command.adminUserSn(),
                command.reason().trim(),
                command.requestId().trim());
    }

    private void lockUser(Long userSn) {
        if (sanctionMapper.lockUser(userSn) == null) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private void requireSameUser(SanctionRecord record, Long userSn) {
        if (!userSn.equals(record.getUserSn())) {
            throw new CustomException(ErrorCode.CONFLICT, "다른 회원에게 사용된 제재 요청 식별자입니다.");
        }
    }
}
