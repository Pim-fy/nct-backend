package nct.member.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.member.mapper.MemberMapper;
import nct.member.port.MemberOperationLockPort;

/** 담당자 7: 여러 도메인이 공유하는 회원별 USERS 행 잠금 계약입니다. */
@Service
@RequiredArgsConstructor
public class MemberOperationLockService implements MemberOperationLockPort {

    private final MemberMapper memberMapper;

    @Override
    @Transactional
    public void lock(Long userSn) {
        if (userSn == null || userSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        memberMapper.findMemberByIdForUpdate(userSn)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
