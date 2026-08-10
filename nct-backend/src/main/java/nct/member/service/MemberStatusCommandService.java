package nct.member.service;

import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.member.domain.Member;
import nct.member.mapper.MemberMapper;
import nct.member.port.MemberStatusChangeCommand;
import nct.member.port.MemberStatusChangeResult;
import nct.member.port.MemberStatusCommandPort;

/**
 * 담당자 7 · F-OPS-019: USERS 행을 잠근 뒤 상태와 Refresh Token을 한 UPDATE로 변경합니다.
 * 관리자 오케스트레이터는 이 계약을 통해서만 계정 접근 상태를 변경합니다.
 */
@Service
@RequiredArgsConstructor
public class MemberStatusCommandService implements MemberStatusCommandPort {

    private static final String ACTIVE = "USRC0001";
    private static final String SUSPENDED = "USRC0002";
    private static final Set<String> CHANGEABLE_STATUSES = Set.of(ACTIVE, SUSPENDED);

    private final MemberMapper memberMapper;

    @Override
    @Transactional
    public MemberStatusChangeResult changeStatus(MemberStatusChangeCommand command) {
        validate(command);

        Member member = memberMapper.findMemberByIdForUpdate(command.userSn())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        String previousStatus = member.getUsrStatusCd();
        if (!CHANGEABLE_STATUSES.contains(previousStatus)) {
            throw new CustomException(
                    ErrorCode.CONFLICT,
                    "탈퇴 계정은 상태를 변경할 수 없습니다.");
        }
        if (previousStatus.equals(command.targetStatusCode())) {
            return new MemberStatusChangeResult(previousStatus, previousStatus, false);
        }

        int changed = memberMapper.updateStatusAndInvalidateRefreshToken(
                command.userSn(),
                previousStatus,
                command.targetStatusCode(),
                String.valueOf(command.actorUserSn()));
        if (changed != 1) {
            throw new CustomException(
                    ErrorCode.CONFLICT,
                    "회원 상태가 다른 요청에 의해 변경되었습니다.");
        }
        return new MemberStatusChangeResult(previousStatus, command.targetStatusCode(), true);
    }

    private void validate(MemberStatusChangeCommand command) {
        if (command == null
                || command.userSn() == null
                || command.userSn() <= 0
                || command.actorUserSn() == null
                || command.actorUserSn() <= 0
                || !CHANGEABLE_STATUSES.contains(command.targetStatusCode())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
